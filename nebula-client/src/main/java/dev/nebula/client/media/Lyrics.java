package dev.nebula.client.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nebula.client.NebulaClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pobiera teksty piosenek z darmowej bazy LRCLIB (lrclib.net), z cache w pamieci. */
public final class Lyrics {
	private Lyrics() {
	}

	public enum State { LOADING, SYNCED, PLAIN_ONLY, NONE, ERROR }

	public static final class Result {
		public volatile State state = State.LOADING;
		public volatile List<LrcParser.Line> lines = List.of();
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
	private static final Map<String, Result> CACHE = new LinkedHashMap<String, Result>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) {
			return size() > 30;
		}
	};

	public static Result get(MediaService.Track t) {
		String key = (t.artist() + "|" + t.title()).toLowerCase();
		synchronized (CACHE) {
			Result e = CACHE.get(key);
			if (e != null) return e;
			e = new Result();
			CACHE.put(key, e);
			final Result entry = e;
			Thread th = new Thread(() -> fetch(t, entry), "nebula-lyrics");
			th.setDaemon(true);
			th.start();
			return e;
		}
	}

	private static void fetch(MediaService.Track t, Result e) {
		try {
			String synced = null;
			boolean anyText = false;

			String exact = "https://lrclib.net/api/get?track_name=" + enc(t.title()) + "&artist_name=" + enc(t.artist())
					+ (t.album().isBlank() ? "" : "&album_name=" + enc(t.album()))
					+ (t.durationSec() > 0 ? "&duration=" + Math.round(t.durationSec()) : "");
			String body = request(exact);
			if (body != null) {
				JsonObject o = JsonParser.parseString(body).getAsJsonObject();
				synced = text(o, "syncedLyrics");
				anyText = text(o, "plainLyrics") != null;
			}
			if (synced == null) {
				// fallback: wyszukiwanie (np. tytul z dopiskiem "Remastered")
				String search = request("https://lrclib.net/api/search?q=" + enc(t.artist() + " " + t.title()));
				if (search != null) {
					JsonArray arr = JsonParser.parseString(search).getAsJsonArray();
					for (JsonElement el : arr) {
						JsonObject o = el.getAsJsonObject();
						String s = text(o, "syncedLyrics");
						if (s != null) {
							synced = s;
							break;
						}
						if (text(o, "plainLyrics") != null) anyText = true;
					}
				}
			}
			if (synced != null) {
				List<LrcParser.Line> lines = LrcParser.parse(synced);
				if (!lines.isEmpty()) {
					e.lines = lines;
					e.state = State.SYNCED;
					return;
				}
			}
			e.state = anyText ? State.PLAIN_ONLY : State.NONE;
		} catch (Exception ex) {
			NebulaClient.LOGGER.warn("Teksty: {}", ex.toString());
			e.state = State.ERROR;
		}
	}

	/** Zwraca cialo odpowiedzi 200 albo null przy 404/bledzie serwera. */
	private static String request(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", "NebulaClient/0.1 (https://github.com/bumbarski/nebula_client)")
				.timeout(Duration.ofSeconds(8)).GET().build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return res.statusCode() == 200 ? res.body() : null;
	}

	private static String text(JsonObject o, String k) {
		if (!o.has(k) || o.get(k).isJsonNull()) return null;
		String s = o.get(k).getAsString();
		return s.isBlank() ? null : s;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}

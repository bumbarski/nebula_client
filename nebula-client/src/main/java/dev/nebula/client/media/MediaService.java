package dev.nebula.client.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nebula.client.NebulaClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Czyta, co aktualnie gra w systemie (Spotify, YouTube w przegladarce, inne odtwarzacze), przez
 * systemowe sterowanie multimediami Windows (SMTC). Nie wymaga logowania ani klucza API.
 * Dziala jeden staly proces PowerShell, ktory co sekunde wypisuje linie JSON.
 */
public final class MediaService {
	private MediaService() {
	}

	public record Track(String title, String artist, String album, double positionSec, double durationSec,
						boolean playing, String app, long sampledAtMs) {
		/** Biezaca pozycja z uwzglednieniem czasu od ostatniej probki. */
		public double position() {
			double p = positionSec;
			if (playing) p += (System.currentTimeMillis() - sampledAtMs) / 1000.0;
			if (durationSec > 0) p = Math.min(p, durationSec);
			return Math.max(0, p);
		}
	}

	private static volatile Track current;
	private static volatile String status = "Wyłączone";
	private static volatile boolean running;
	private static volatile Process process;
	private static Thread thread;

	public static Track current() {
		return current;
	}

	public static String status() {
		return status;
	}

	public static synchronized void start() {
		if (running) return;
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			status = "Obsługiwane tylko na Windows";
			return;
		}
		running = true;
		status = "Uruchamianie...";
		Runtime.getRuntime().addShutdownHook(new Thread(MediaService::destroyProcess));
		thread = new Thread(MediaService::loop, "nebula-media");
		thread.setDaemon(true);
		thread.start();
	}

	public static synchronized void stop() {
		running = false;
		destroyProcess();
		current = null;
		status = "Wyłączone";
	}

	private static void destroyProcess() {
		Process p = process;
		if (p != null) p.destroy();
		process = null;
	}

	private static void loop() {
		int failures = 0;
		while (running) {
			boolean gotLine = false;
			try {
				String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
				ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
						"-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
				pb.redirectErrorStream(false);
				Process p = pb.start();
				process = p;
				try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while (running && (line = r.readLine()) != null) {
						if (line.isBlank()) continue;
						gotLine = true;
						handle(line);
					}
				}
			} catch (Exception e) {
				NebulaClient.LOGGER.warn("Media: {}", e.toString());
			}
			if (!running) break;
			failures = gotLine ? 0 : failures + 1;
			if (failures >= 4) {
				status = "Nie udało się odczytać odtwarzacza";
				running = false;
				break;
			}
			try {
				Thread.sleep(3000);
			} catch (InterruptedException ignored) {
				break;
			}
		}
	}

	/** Rozbiera jedna linie JSON z PowerShella (public, zeby dalo sie testowac). */
	static void handle(String line) {
		try {
			JsonObject o = JsonParser.parseString(line).getAsJsonObject();
			if (!o.has("ok") || !o.get("ok").getAsBoolean()) {
				current = null;
				status = "Nic nie gra";
				return;
			}
			current = new Track(str(o, "title"), str(o, "artist"), str(o, "album"),
					num(o, "pos"), num(o, "dur"), o.has("playing") && o.get("playing").getAsBoolean(),
					str(o, "app"), System.currentTimeMillis());
			status = "Połączono";
		} catch (RuntimeException e) {
			// uszkodzona linia: pomijamy
		}
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	private static double num(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0;
	}

	private static final String SCRIPT = String.join("\n",
			"$ErrorActionPreference = 'SilentlyContinue'",
			"[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
			"Add-Type -AssemblyName System.Runtime.WindowsRuntime",
			"$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
			"function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }",
			"[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]",
			"[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]",
			"$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
			"while ($true) {",
			"  $out = @{ ok = $false }",
			"  try {",
			"    $s = $null",
			"    foreach ($x in $mgr.GetSessions()) { if ($x.SourceAppUserModelId -like '*Spotify*') { $s = $x; break } }",
			"    if (-not $s) { $s = $mgr.GetCurrentSession() }",
			"    if ($s) {",
			"      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
			"      $tl = $s.GetTimelineProperties()",
			"      $playing = ([int]$s.GetPlaybackInfo().PlaybackStatus -eq 4)",
			"      $pos = $tl.Position.TotalSeconds",
			"      if ($playing) { $pos += ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime.ToUniversalTime()).TotalSeconds }",
			"      $dur = $tl.EndTime.TotalSeconds - $tl.StartTime.TotalSeconds",
			"      if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }",
			"      if ($pos -lt 0) { $pos = 0 }",
			"      if ($p.Title) {",
			"        $out = @{ ok = $true; title = $p.Title; artist = $p.Artist; album = $p.AlbumTitle; pos = [math]::Round($pos, 2); dur = [math]::Round($dur, 2); playing = $playing; app = $s.SourceAppUserModelId }",
			"      }",
			"    }",
			"  } catch { }",
			"  [Console]::Out.WriteLine(($out | ConvertTo-Json -Compress))",
			"  [Console]::Out.Flush()",
			"  Start-Sleep -Milliseconds 1000",
			"}");
}

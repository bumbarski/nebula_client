package dev.nebula.client.media;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser tekstow z synchronizacja czasu (format LRC: "[mm:ss.xx] tekst"). Bez zaleznosci, wiec latwy do testow. */
public final class LrcParser {
	private LrcParser() {
	}

	public record Line(double time, String text) {
	}

	private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2}(?:[.:]\\d{1,3})?)]");

	/** Zwraca linie posortowane po czasie. Puste linie zamieniane sa na symbol przerwy. */
	public static List<Line> parse(String lrc) {
		List<Line> out = new ArrayList<>();
		if (lrc == null) return out;
		for (String raw : lrc.split("\\r?\\n")) {
			Matcher m = STAMP.matcher(raw);
			List<Double> times = new ArrayList<>();
			int end = 0;
			while (m.find() && m.start() == end) {
				double min = Double.parseDouble(m.group(1));
				double sec = Double.parseDouble(m.group(2).replace(':', '.'));
				times.add(min * 60 + sec);
				end = m.end();
			}
			if (times.isEmpty()) continue; // tagi [ar:...], [ti:...] itp.
			String text = raw.substring(end).trim();
			if (text.isEmpty()) text = "...";
			for (double t : times) out.add(new Line(t, text));
		}
		out.sort(Comparator.comparingDouble(Line::time));
		return out;
	}

	/** Indeks ostatniej linii, ktorej czas minal; -1 przed pierwsza. */
	public static int indexAt(List<Line> lines, double seconds) {
		int lo = 0;
		int hi = lines.size() - 1;
		int ans = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).time() <= seconds) {
				ans = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return ans;
	}
}

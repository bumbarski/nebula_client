package dev.nebula.client.media;

/** LRCLIB daje czasy linii, nie slow, wiec czasy slow wyliczamy proporcjonalnie do dlugosci slow. */
public final class WordTiming {
	private WordTiming() {
	}

	public static String[] split(String text) {
		String t = text == null ? "" : text.trim();
		return t.isEmpty() ? new String[0] : t.split("\\s+");
	}

	/** Indeks slowa spiewanego w chwili pos (sekundy) dla linii trwajacej od start do end; zawsze w 0..n-1. */
	public static int wordAt(String[] words, double start, double end, double pos) {
		int n = words.length;
		if (n <= 1 || end <= start) return 0;
		double total = 0;
		for (String w : words) total += Math.max(2, w.length() + 1);
		double frac = Math.max(0, Math.min(0.9999, (pos - start) / (end - start)));
		double acc = 0;
		for (int i = 0; i < n; i++) {
			acc += Math.max(2, words[i].length() + 1) / total;
			if (frac < acc) return i;
		}
		return n - 1;
	}
}

package dev.nebula.client.ui;

/** Proste animacje: wygladzanie wartosci w czasie i funkcje ease. Wzorowane na rozwiazaniach z modu LyricsFlow (374m, za zgoda autora). */
public final class Anim {
	private Anim() {
	}

	private static long lastNs;
	private static float dt = 0.016f;

	/** Wolaj co klatke (z rysowania); zwraca czas klatki w sekundach (max 0.1). */
	public static float frame() {
		long now = System.nanoTime();
		if (lastNs != 0L) {
			float d = (now - lastNs) / 1.0e9f;
			if (d > 0f) dt = Math.min(0.1f, d);
		}
		lastNs = now;
		return dt;
	}

	public static float dt() {
		return dt;
	}

	/** Dazy cur do target wykladniczo; speed to "szybkosc" (im wiecej, tym szybciej). */
	public static float approach(float cur, float target, float speed) {
		float k = 1f - (float) Math.exp(-speed * dt);
		return cur + (target - cur) * k;
	}

	public static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(v, 1f);
	}

	public static float outCubic(float t) {
		float u = 1f - clamp01(t);
		return 1f - u * u * u;
	}
}

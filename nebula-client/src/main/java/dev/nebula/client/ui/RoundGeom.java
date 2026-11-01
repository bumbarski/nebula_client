package dev.nebula.client.ui;

/**
 * Geometria zaokraglonych prostokatow jako poziome pasy z wygladzonym (antyaliasowanym) brzegiem.
 * Czysta matematyka bez zaleznosci od Minecrafta, wiec daje sie przetestowac poza gra.
 */
public final class RoundGeom {
	private RoundGeom() {
	}

	public interface Sink {
		/** Pas w wierszu y od x1 (wlacznie) do x2 (wylacznie), pokrycie 0..1 (1 = pelny kolor). */
		void span(int y, int x1, int x2, float cover);
	}

	/** Wypelniony zaokraglony prostokat o rozmiarze w x h (wspolrzedne wzgledne). */
	public static void fill(int w, int h, int r, Sink s) {
		r = clampRadius(w, h, r);
		for (int y = 0; y < h; y++) {
			double dx = inset(y, h, r);
			int full = (int) Math.ceil(dx - 1e-9);
			double frac = full - dx;
			if (full * 2 >= w) {
				full = w / 2;
				frac = 0;
			}
			s.span(y, full, w - full, 1f);
			if (frac > 0.04 && full >= 1) {
				s.span(y, full - 1, full, (float) frac);
				s.span(y, w - full, w - full + 1, (float) frac);
			}
		}
	}

	/** Obwodka o grubosci 1 piksela wzdluz tego samego ksztaltu. */
	public static void outline(int w, int h, int r, Sink s) {
		r = clampRadius(w, h, r);
		for (int y = 0; y < h; y++) {
			double dxo = inset(y, h, r);
			int outer = (int) Math.ceil(dxo - 1e-9);
			double frac = outer - dxo;
			if (outer * 2 >= w) outer = w / 2;
			if (y == 0 || y == h - 1) {
				s.span(y, outer, w - outer, 1f);
				continue;
			}
			int ir = Math.max(0, r - 1);
			int ih = h - 2;
			int iw = w - 2;
			double dxi = inset(y - 1, ih, ir);
			int inner = 1 + (int) Math.ceil(dxi - 1e-9);
			if (inner <= outer) inner = outer + 1;
			if (inner * 2 > w) inner = w / 2;
			s.span(y, outer, inner, 1f);
			s.span(y, w - inner, w - outer, 1f);
			if (frac > 0.04 && outer >= 1) {
				s.span(y, outer - 1, outer, (float) frac);
				s.span(y, w - outer, w - outer + 1, (float) frac);
			}
		}
	}

	private static int clampRadius(int w, int h, int r) {
		return Math.max(0, Math.min(r, Math.min(w, h) / 2));
	}

	/** O ile (w pikselach, ulamkowo) brzeg w wierszu y jest odsuniety od lewej krawedzi. */
	static double inset(int y, int h, int r) {
		if (r <= 0) return 0;
		double dy;
		if (y < r) dy = r - (y + 0.5);
		else if (y >= h - r) dy = (y + 0.5) - (h - r);
		else return 0;
		return r - Math.sqrt(Math.max(0, (double) r * r - dy * dy));
	}
}

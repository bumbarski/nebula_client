package dev.nebula.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Cienka warstwa nad rysowaniem GUI. Cale rysowanie w kliencie idzie przez te metody,
 * wiec gdy Mojang zmieni nazwy w API, poprawiasz je tylko tutaj.
 */
public final class Gfx {
	private Gfx() {
	}

	/** Mnozy kanal alfa koloru ARGB przez a (0..1). */
	public static int alpha(int argb, float a) {
		int base = (argb >>> 24) & 0xFF;
		int na = Math.max(0, Math.min(255, Math.round(base * a)));
		return (na << 24) | (argb & 0x00FFFFFF);
	}

	/** Plynne przejscie miedzy dwoma kolorami ARGB (wlacznie z alfa). */
	public static int lerp(int from, int to, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int out = 0;
		for (int shift = 24; shift >= 0; shift -= 8) {
			int a = (from >>> shift) & 0xFF;
			int b = (to >>> shift) & 0xFF;
			out |= Math.round(a + (b - a) * t) << shift;
		}
		return out;
	}

	public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + h, color);
	}

	/** Pionowy gradient od c1 (gora) do c2 (dol). */
	public static void gradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int c1, int c2) {
		g.fillGradient(x, y, x + w, y + h, c1, c2);
	}

	public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.outline(x, y, w, h, color);
	}

	/**
	 * Zaokraglony prostokat zlozony z poziomych pasow. Pasy sie nie nakladaja,
	 * wiec dziala poprawnie takze z polprzezroczystymi kolorami.
	 */
	public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			rect(g, x, y, w, h, color);
			return;
		}
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
		}
		g.fill(x, y + r, x + w, y + h - r, color);
	}

	/** Zaczyna rysowanie w skali s (kazde pushScale musi miec popScale). */
	public static void pushScale(GuiGraphicsExtractor g, float s) {
		g.pose().pushMatrix();
		g.pose().scale(s, s);
	}

	public static void popScale(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
	}

	/**
	 * Tlo elementu HUD/okna: w stylu "Szklo" przezroczysta ciemna tafla z jasnym polyskiem u gory
	 * i cienka krawedzia, w stylu "Ciemny" zwykle zaokraglone tlo.
	 */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float a) {
		if (!Theme.glass()) {
			roundRect(g, x, y, w, h, r, alpha(Theme.BG, Theme.opacity() * a));
			return;
		}
		float op = Theme.opacity();
		roundRect(g, x, y, w, h, r, alpha(0xFF0C0F18, (0.28f + 0.40f * op) * a));
		glassShine(g, x, y, w, h, r, a);
	}

	/** Polysk szkla: jasny gradient u gory, poswiata akcentu na dole i krawedzie swiatla. */
	public static void glassShine(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float a) {
		int rr = Math.min(r, Math.max(1, h / 2));
		int white = 0xFFFFFFFF;
		gradient(g, x + rr, y + 1, w - 2 * rr, Math.max(1, h / 2), alpha(white, 0.17f * a), alpha(white, 0f));
		gradient(g, x + rr, y + h - Math.max(2, h / 3) - 1, w - 2 * rr, Math.max(2, h / 3),
				alpha(Theme.accent(), 0f), alpha(Theme.accent(), 0.13f * a));
		rect(g, x + rr, y, w - 2 * rr, 1, alpha(white, 0.55f * a));
		rect(g, x + rr, y + h - 1, w - 2 * rr, 1, alpha(white, 0.13f * a));
		rect(g, x, y + rr, 1, h - 2 * rr, alpha(white, 0.24f * a));
		rect(g, x + w - 1, y + rr, 1, h - 2 * rr, alpha(white, 0.12f * a));
	}

	/** Skraca tekst do szerokosci maxW (w pikselach), dodajac "...". */
	public static String fit(String s, int maxW) {
		if (width(s) <= maxW) return s;
		String dots = "...";
		while (s.length() > 1 && width(s + dots) > maxW) s = s.substring(0, s.length() - 1);
		return s + dots;
	}

	public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color, boolean shadow) {
		g.text(Minecraft.getInstance().font, s, x, y, color, shadow);
	}

	public static int width(String s) {
		return Minecraft.getInstance().font.width(s);
	}

	public static int fontHeight() {
		return Minecraft.getInstance().font.lineHeight;
	}
}

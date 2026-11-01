package dev.nebula.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntUnaryOperator;

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

	/** Zaokraglony prostokat z wygladzonymi (antyaliasowanymi) narozami. */
	public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		roundRectRows(g, x, y, w, h, r, row -> color);
	}

	/** Jak roundRect, ale kolor moze zalezec od wiersza (gradienty pionowe przycinane do ksztaltu). */
	public static void roundRectRows(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, IntUnaryOperator rowColor) {
		RoundGeom.fill(w, h, r, (row, x1, x2, cover) -> {
			int c = rowColor.applyAsInt(row);
			if ((c >>> 24) == 0) return;
			if (cover < 1f) c = alpha(c, cover);
			g.fill(x + x1, y + row, x + x2, y + row + 1, c);
		});
	}

	/** Obwodka (1 px) zaokraglonego prostokata, kolor zalezny od wiersza. */
	public static void roundOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, IntUnaryOperator rowColor) {
		RoundGeom.outline(w, h, r, (row, x1, x2, cover) -> {
			int c = rowColor.applyAsInt(row);
			if ((c >>> 24) == 0) return;
			if (cover < 1f) c = alpha(c, cover);
			g.fill(x + x1, y + row, x + x2, y + row + 1, c);
		});
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

	/** Polysk szkla: jasny gradient u gory, poswiata akcentu na dole i obwodka swiatla, wszystko w ksztalcie zaokraglonym. */
	public static void glassShine(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float a) {
		final int white = 0xFFFFFFFF;
		final int accent = Theme.accent();
		final int topH = Math.max(2, h / 2);
		final int glowH = Math.max(2, h / 3);
		roundRectRows(g, x, y, w, h, r, row -> {
			if (row < topH) return alpha(white, 0.17f * a * (1f - (float) row / topH));
			int fromBottom = h - 1 - row;
			if (fromBottom < glowH) return alpha(accent, 0.13f * a * (1f - (float) fromBottom / glowH));
			return 0;
		});
		final int last = Math.max(1, h - 1);
		roundOutline(g, x, y, w, h, r, row -> alpha(white, (0.52f - 0.40f * row / last) * a));
	}

	/** Linia 1 px miedzy dwoma punktami ekranu (obrocony prostokat, wiec jeden fill na linie). */
	public static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int color) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.75f) {
			int px = Math.round(x1);
			int py = Math.round(y1);
			g.fill(px, py, px + 1, py + 1, color);
			return;
		}
		if (dy == 0f) {
			int py = Math.round(y1);
			g.fill(Math.round(Math.min(x1, x2)), py, Math.round(Math.max(x1, x2)) + 1, py + 1, color);
			return;
		}
		if (dx == 0f) {
			int px = Math.round(x1);
			g.fill(px, Math.round(Math.min(y1, y2)), px + 1, Math.round(Math.max(y1, y2)) + 1, color);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x1, y1);
		g.pose().rotate((float) Math.atan2(dy, dx));
		g.fill(0, 0, Math.max(1, Math.round(len)), 1, color);
		g.pose().popMatrix();
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

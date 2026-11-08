package dev.nebula.client.module.impl;

import dev.lyricsflow.app.Core;
import dev.lyricsflow.app.sky.SkyPreset;
import dev.nebula.client.setting.CustomSetting;
import dev.nebula.client.ui.Gfx;
import dev.nebula.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/**
 * Siatka wyboru nieba z zywym podgladem (kolory, gwiazdy, zorza, ksiezyc) dla presetow modu LyricsFlow
 * (autor: 374m / cmonmanx, uzyty za jego zgoda). Pierwszy kafelek wylacza niebo.
 */
final class SkyGridSetting extends CustomSetting {
	private static final int GAP = 6;
	private static final int MIN_W = 84;
	private final SkyPreset[] presets;

	SkyGridSetting() {
		super("Niebo");
		SkyPreset[] all = SkyPreset.order();
		java.util.List<SkyPreset> list = new java.util.ArrayList<>();
		for (SkyPreset p : all) if (p != SkyPreset.CUSTOM) list.add(p);
		presets = list.toArray(new SkyPreset[0]);
	}

	private int cols(int w) {
		return Math.max(2, (w + GAP) / (MIN_W + GAP));
	}

	private int cellW(int w) {
		int c = cols(w);
		return (w - GAP * (c - 1)) / c;
	}

	private int picH(int w) {
		return Math.round(cellW(w) * 0.5625f);
	}

	private int cellH(int w) {
		return picH(w) + 13;
	}

	@Override
	public int height(int width) {
		int total = presets.length + 1;
		int rows = (total + cols(width) - 1) / cols(width);
		return 14 + rows * (cellH(width) + GAP);
	}

	private static int current() {
		try {
			return Core.cfg().sky;
		} catch (Throwable t) {
			return -1;
		}
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float a) {
		Gfx.text(g, "Niebo (podgląd na żywo)", x, y, Gfx.alpha(0xFFFFFFFF, a), false);
		int cols = cols(w);
		int cw = cellW(w);
		int ph = picH(w);
		int sel = current();
		double time = System.nanoTime() / 1.0e9;
		for (int i = 0; i < presets.length + 1; i++) {
			int cx = x + (i % cols) * (cw + GAP);
			int cy = y + 14 + (i / cols) * (cellH(w) + GAP);
			boolean over = mx >= cx && mx < cx + cw && my >= cy && my < cy + cellH(w);
			SkyPreset p = i == 0 ? null : presets[i - 1];
			boolean on = p == null ? sel < 0 : sel == p.ordinal();
			if (on) Gfx.roundRect(g, cx - 2, cy - 2, cw + 4, ph + 4, 8, Gfx.alpha(Theme.accent(), 0.30f * a));
			if (p == null) {
				Gfx.roundRect(g, cx, cy, cw, ph, 6, Gfx.alpha(0xFF14141F, a));
				String off = "Wyłączone";
				Gfx.text(g, off, cx + (cw - Gfx.width(off)) / 2, cy + ph / 2 - 4, Gfx.alpha(0xFF9A9AB8, a), false);
			} else {
				preview(g, p, cx, cy, cw, ph, time, a);
			}
			final boolean fon = on;
			Gfx.roundOutline(g, cx, cy, cw, ph, 6, row -> Gfx.alpha(fon ? Theme.accent() : 0xFFFFFFFF, (fon ? 0.95f : over ? 0.30f : 0.10f) * a));
			String name = p == null ? "Brak" : pretty(p.name());
			Gfx.text(g, Gfx.fit(name, cw), cx + 1, cy + ph + 3, Gfx.alpha(on ? 0xFFFFFFFF : over ? 0xFFD8D8F0 : 0xFF9A9AB8, a), false);
		}
	}

	private static String pretty(String n) {
		String s = n.toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** Maly, animowany podglad nieba: gradient, gwiazdy, zorza i ksiezyc. */
	private static void preview(GuiGraphicsExtractor g, SkyPreset p, int x, int y, int w, int h, double time, float a) {
		final int top = 0xFF000000 | p.top;
		final int hor = 0xFF000000 | p.horizon;
		Gfx.roundRectRows(g, x, y, w, h, 6, row -> Gfx.alpha(Gfx.lerp(top, hor, (float) Math.pow(row / (float) Math.max(1, h - 1), 1.3)), a));
		int n = 4 + Math.round(p.stars * 9f);
		long seed = p.ordinal() * 7919L + 13;
		for (int i = 0; i < n; i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int sx = x + 3 + (int) ((seed >>> 33) % Math.max(1, w - 6));
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int sy = y + 2 + (int) ((seed >>> 33) % Math.max(1, h * 2 / 3));
			float tw = 0.55f + 0.45f * (float) Math.sin(time * 2.0 + i);
			Gfx.rect(g, sx, sy, 1, 1, Gfx.alpha(0xFFFFFFFF, tw * a));
		}
		if (p.aurora) {
			for (int xi = 3; xi < w - 3; xi += 2) {
				double wave = Math.sin(xi * 0.17 + time * 0.9) * h * 0.07;
				int by = y + (int) (h * 0.30 + wave);
				Gfx.rect(g, x + xi, by, 2, Math.max(2, h / 6), Gfx.alpha(0xFF000000 | p.aurora1, 0.38f * a));
				Gfx.rect(g, x + xi, by + Math.max(2, h / 6), 2, Math.max(2, h / 8), Gfx.alpha(0xFF000000 | p.aurora2, 0.24f * a));
			}
		}
		if (p.moon != 0) {
			Gfx.circle(g, x + w * 0.78f, y + h * 0.28f, Math.max(2.5f, h * 0.10f), Gfx.alpha(0xFF000000 | p.moon, 0.95f * a));
		}
		if (p.shootingStars) {
			float ph = (float) ((time * 0.4 + p.ordinal() * 0.37) % 1.0);
			if (ph < 0.25f) {
				float k = ph / 0.25f;
				Gfx.line(g, x + w * (0.15f + 0.5f * k), y + h * (0.10f + 0.25f * k), x + w * (0.15f + 0.5f * k) - 5, y + h * (0.10f + 0.25f * k) - 2, Gfx.alpha(0xFFFFFFFF, (1f - k) * a));
			}
		}
	}

	@Override
	public boolean click(int x, int y, int w, double mx, double my, int button) {
		int cols = cols(w);
		int cw = cellW(w);
		int ch = cellH(w);
		for (int i = 0; i < presets.length + 1; i++) {
			int cx = x + (i % cols) * (cw + GAP);
			int cy = y + 14 + (i / cols) * (ch + GAP);
			if (mx < cx || mx >= cx + cw || my < cy || my >= cy + ch) continue;
			try {
				if (i == 0) {
					Core.cfg().sky = -1;
				} else {
					int o = presets[i - 1].ordinal();
					Core.cfg().sky = o;
					Core.cfg().lastSky = o;
				}
				Core.saveConfig();
			} catch (Throwable ignored) {
				// konfiguracja LyricsFlow jeszcze nie gotowa
			}
			return true;
		}
		return false;
	}
}

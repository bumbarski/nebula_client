package dev.nebula.client.module.impl;

import dev.nebula.client.media.Lyrics;
import dev.nebula.client.media.LrcParser;
import dev.nebula.client.media.MediaService;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.ui.Gfx;
import dev.nebula.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Karta "teraz gra": tytul, wykonawca, pasek postepu i tekst piosenki zsynchronizowany z odtwarzaczem. */
public final class MusicModule extends HudModule {
	private final BoolSetting progress = add(new BoolSetting("Pasek postępu", true));
	private final BoolSetting lyrics = add(new BoolSetting("Pokaż tekst piosenki", true));
	private final NumberSetting lyricLines = add(new NumberSetting("Linie tekstu", 1, 7, 3, 2));
	private final NumberSetting cardWidth = add(new NumberSetting("Szerokość", 120, 280, 180, 10));

	public MusicModule() {
		super("music", "Muzyka (Spotify)", "Tytuł, postęp i tekst piosenki z odtwarzacza w systemie", Category.HUD, false, 4, 110);
	}

	@Override
	protected void onEnable() {
		MediaService.start();
	}

	@Override
	protected void onDisable() {
		MediaService.stop();
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		MediaService.Track t = MediaService.current();
		boolean demo = false;
		if (t == null) {
			if (!preview) {
				hide();
				return;
			}
			t = new MediaService.Track("Tytuł piosenki", "Wykonawca", "", 83, 215, true, "", System.currentTimeMillis());
			demo = true;
		}

		final int pad = 6;
		int cw = cardWidth.getInt();
		int inner = cw - pad * 2;
		int rows = 2;
		boolean bar = progress.value;
		boolean text = lyrics.value;
		int lines = Math.max(1, lyricLines.getInt() | 1);

		int hgt = pad * 2 + 10 + 11;
		if (bar) hgt += 12;
		if (text) hgt += 6 + lines * 11;
		w = cw;
		h = hgt;

		Gfx.panel(g, x, y, w, h, 6, 1f);
		Gfx.roundRect(g, x + 3, y + 6, 2, h - 12, 1, Theme.accent());

		int tx = x + pad + 3;
		inner -= 3;
		int ty = y + pad;
		String mark = t.playing() ? "> " : "II ";
		Gfx.text(g, mark, tx, ty, Theme.accent(), shadow.value);
		int mw = Gfx.width(mark);
		Gfx.text(g, Gfx.fit(t.title(), inner - mw), tx + mw, ty, 0xFFFFFFFF, shadow.value);
		ty += 11;
		Gfx.text(g, Gfx.fit(t.artist(), inner), tx, ty, Theme.MUTED, shadow.value);
		ty += 11;

		double pos = t.position();
		if (bar) {
			String l = clock(pos);
			String r = t.durationSec() > 0 ? clock(t.durationSec()) : "--:--";
			int lw = Gfx.width(l);
			int rw = Gfx.width(r);
			int bx = tx + lw + 4;
			int bw = Math.max(10, inner - lw - rw - 8);
			Gfx.text(g, l, tx, ty + 1, Theme.MUTED, false);
			Gfx.text(g, r, tx + inner - rw, ty + 1, Theme.MUTED, false);
			Gfx.roundRect(g, bx, ty + 3, bw, 3, 1, Gfx.alpha(0xFFFFFFFF, 0.18f));
			float frac = t.durationSec() > 0 ? (float) Math.min(1.0, pos / t.durationSec()) : 0f;
			Gfx.roundRect(g, bx, ty + 3, Math.max(2, Math.round(bw * frac)), 3, 1, Theme.accent());
			ty += 12;
		}

		if (text) {
			ty += 4;
			drawLyrics(g, t, pos, tx, ty, inner, lines, demo);
		}
	}

	private void drawLyrics(GuiGraphicsExtractor g, MediaService.Track t, double pos, int tx, int ty, int inner,
							int lines, boolean demo) {
		String[] rows = new String[lines];
		int centre = lines / 2;
		int current = centre;

		if (demo) {
			String[] sample = {"Poprzednia linia tekstu", "Aktualnie śpiewana linia", "Następna linia tekstu"};
			for (int i = 0; i < lines; i++) {
				int k = i - centre + 1;
				rows[i] = k >= 0 && k < sample.length ? sample[k] : "";
			}
		} else {
			Lyrics.Entry e = Lyrics.get(t);
			switch (e.state) {
				case LOADING -> rows[centre] = "Szukam tekstu...";
				case NONE -> rows[centre] = "Brak tekstu";
				case PLAIN_ONLY -> rows[centre] = "Tekst bez synchronizacji";
				case ERROR -> rows[centre] = "Nie udało się pobrać tekstu";
				case SYNCED -> {
					List<LrcParser.Line> ls = e.lines;
					int idx = LrcParser.indexAt(ls, pos + 0.25);
					for (int i = 0; i < lines; i++) {
						int li = idx + (i - centre);
						rows[i] = li >= 0 && li < ls.size() ? ls.get(li).text() : "";
					}
				}
			}
		}

		for (int i = 0; i < lines; i++) {
			String s = rows[i];
			if (s != null && !s.isEmpty()) {
				boolean cur = i == current;
				int dist = Math.abs(i - current);
				int color = cur ? 0xFFFFFFFF : Gfx.alpha(Theme.MUTED, Math.max(0.35f, 0.85f - 0.25f * dist));
				String fit = Gfx.fit(s, inner);
				int px = tx + (inner - Gfx.width(fit)) / 2;
				Gfx.text(g, fit, px, ty + i * 11, color, cur && shadow.value);
			}
		}
	}

	private static String clock(double sec) {
		int s = (int) Math.max(0, Math.round(sec));
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}
}

package dev.nebula.client.module.impl;

import dev.nebula.client.media.LrcParser;
import dev.nebula.client.media.Lyrics;
import dev.nebula.client.media.MediaService;
import dev.nebula.client.media.WordTiming;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.ui.Anim;
import dev.nebula.client.ui.Gfx;
import dev.nebula.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Odtwarzacz "teraz gra". Styl "Pigulka" (domyslny) to smukla kapsula z tytulem lub aktualnie spiewana linia z
 * podswietlanym slowem i animowanym equalizerem - wzorowana na pasku z modu LyricsFlow (autor: 374m / cmonmanx,
 * uzyte za jego zgoda). Styl "Karta" to klasyczna karta z paskiem postepu i kilkoma liniami tekstu.
 */
public final class MusicModule extends HudModule {
	private final ModeSetting style = add(new ModeSetting("Styl", new String[]{"Pigułka", "Karta"}, 0));
	private final BoolSetting karaoke = add(new BoolSetting("Aktualna linia tekstu w pigułce", true));
	private final BoolSetting progress = add(new BoolSetting("Pasek postępu (karta)", true));
	private final BoolSetting lyrics = add(new BoolSetting("Pokaż tekst piosenki (karta)", true));
	private final NumberSetting lyricLines = add(new NumberSetting("Linie tekstu (karta)", 1, 7, 3, 2));
	private final NumberSetting cardWidth = add(new NumberSetting("Szerokość karty", 120, 280, 180, 10));

	// stan animacji pigulki
	private String trackKey = "";
	private long trackSince;
	private String contentKey = "";
	private float swap = 1f;
	private float width = 150f;
	private float scroll;
	private float hlX;
	private float hlW;
	private float vis;
	private final float[] bars = new float[4];
	private float spin;
	private float beat;
	private int lastWordKey = Integer.MIN_VALUE;

	public MusicModule() {
		super("music", "Muzyka (Spotify)", "Tytuł, postęp i tekst piosenki z odtwarzacza w systemie", Category.HUD, false, 4, 110);
	}

	@Override
	protected void onEnable() {
		MediaService.acquire();
	}

	@Override
	protected void onDisable() {
		MediaService.release();
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		MediaService.Track t = MediaService.current();
		boolean demo = false;
		if (t == null) {
			vis = 0f;
			if (!preview) {
				hide();
				return;
			}
			t = new MediaService.Track("Tytuł piosenki", "Wykonawca", "", 83, 215, true, "", System.currentTimeMillis());
			demo = true;
		}
		Anim.frame();
		if (style.index() == 0) drawPill(g, t, demo);
		else drawCard(g, t, demo);
	}

	// ---------------------------------------------------------------- pigulka

	private void drawPill(GuiGraphicsExtractor g, MediaService.Track t, boolean demo) {
		String key = t.artist() + "|" + t.title();
		if (!key.equals(trackKey)) {
			trackKey = key;
			trackSince = System.currentTimeMillis();
			swap = 0f;
		}
		vis = Anim.approach(vis, 1f, 7f);

		// aktualna linia i slowo
		String line = null;
		String[] words = null;
		int word = -1;
		double pos = t.position();
		long sinceTrack = System.currentTimeMillis() - trackSince;
		if (karaoke.value && !demo && t.playing() && sinceTrack > 3200L) {
			Lyrics.Result e = Lyrics.get(t);
			if (e.state == Lyrics.State.SYNCED) {
				List<LrcParser.Line> ls = e.lines;
				int idx = LrcParser.indexAt(ls, pos + 0.15);
				if (idx >= 0 && !ls.get(idx).text().equals("...")) {
					double start = ls.get(idx).time();
					double next = idx + 1 < ls.size() ? ls.get(idx + 1).time() : start + 5;
					double end = Math.min(next, start + 8);
					if (pos + 0.15 <= end + 1.5) {
						line = ls.get(idx).text();
						words = WordTiming.split(line);
						word = WordTiming.wordAt(words, start, end, pos + 0.15);
						String k2 = "L" + idx;
						if (!k2.equals(contentKey)) {
							contentKey = k2;
							swap = 0f;
							scroll = 0f;
							hlW = 0f;
						}
					}
				}
			}
		}
		boolean showLyrics = line != null && words.length > 0;
		String status = !t.playing() && !demo ? "Pauza" : null;
		if (!showLyrics) {
			String k2 = (status != null ? "S" : "T") + trackKey;
			if (!k2.equals(contentKey)) {
				contentKey = k2;
				swap = 0f;
			}
		}
		swap = Anim.approach(swap, 1f, 9f);

		if (showLyrics) {
			int wk = contentKey.hashCode() * 31 + word;
			if (wk != lastWordKey) {
				lastWordKey = wk;
				beat = 1f;
			}
		}
		beat = Math.max(0f, beat - Anim.dt() * 3.2f);

		final int h0 = 18;
		String title = t.title();
		String artist = t.artist();
		String plain = status != null ? status + "  ·  " + title : (artist.isEmpty() ? title : title + "  ·  " + artist);
		String text = showLyrics ? line : plain;
		int textW = Gfx.width(text);
		float iconW = h0;
		float barsW = 14f;
		float pad = 7f;
		float maxText = 190f;
		float targetW = Math.max(150f, iconW + Math.min(textW, maxText) + barsW + pad * 2f);
		width = Anim.approach(width, targetW, 8f);
		int pw = Math.round(width);
		w = pw;
		h = h0;
		float a = Anim.outCubic(vis) * (t.playing() || demo ? 1f : 0.7f);
		int accent = Theme.accent();
		int r = h0 / 2;

		if (background.value) {
			// miekki cien i ciemna kapsula z cienka jasna obwodka
			for (int i = 3; i >= 1; i--) {
				Gfx.roundRect(g, x - i, y - i + 1, pw + i * 2, h0 + i * 2, r + i, Gfx.alpha(0xFF000000, a * 0.07f));
			}
			Gfx.roundRect(g, x, y, pw, h0, r, Gfx.alpha(Theme.glass() ? 0xFF10101A : 0xFF14141E, a * (0.78f + 0.2f * Theme.opacity())));
			if (Theme.glass()) Gfx.glassShine(g, x, y, pw, h0, r, a * 0.8f);
			else Gfx.roundOutline(g, x, y, pw, h0, r, row -> Gfx.alpha(0xFFFFFFFF, a * 0.16f));
			if (beat > 0.05f) {
				float ba = a * 0.55f * beat;
				Gfx.roundOutline(g, x, y, pw, h0, r, row -> Gfx.alpha(accent, ba));
			}
		}

		// ikona: obracajaca sie plyta (pauza: dwie kreski)
		float icx = x + h0 / 2f + 1f;
		float icy = y + h0 / 2f;
		float ir = h0 * 0.34f;
		if (status != null) {
			Gfx.circle(g, icx, icy, ir, Gfx.alpha(0xFFE8E8F2, a));
			g.fill(Math.round(icx - ir * 0.42f), Math.round(icy - ir * 0.45f), Math.round(icx - ir * 0.14f), Math.round(icy + ir * 0.45f), Gfx.alpha(0xFF15151F, a));
			g.fill(Math.round(icx + ir * 0.14f), Math.round(icy - ir * 0.45f), Math.round(icx + ir * 0.42f), Math.round(icy + ir * 0.45f), Gfx.alpha(0xFF15151F, a));
		} else {
			spin += Anim.dt() * 2.4f;
			Gfx.circle(g, icx, icy, ir, Gfx.alpha(Gfx.lerp(accent, 0xFFFFFFFF, 0.25f), a));
			Gfx.circle(g, icx, icy, ir * 0.72f, Gfx.alpha(0xFF14141E, a));
			float sx = (float) Math.cos(spin) * ir * 0.45f;
			float sy = (float) Math.sin(spin) * ir * 0.45f;
			Gfx.circle(g, icx + sx, icy + sy, Math.max(1f, ir * 0.16f), Gfx.alpha(0xFFFFFFFF, a * 0.9f));
			Gfx.circle(g, icx, icy, Math.max(1f, ir * 0.22f), Gfx.alpha(accent, a));
		}

		// tekst (z przycieciem), karaoke z podswietlonym slowem albo tytul
		int tx = Math.round(x + iconW + 2f);
		int tw = Math.round(width - iconW - barsW - pad - 2f);
		float slide = (1f - Anim.outCubic(swap)) * 6f;
		float ta = a * Anim.outCubic(swap);
		int ty = Math.round(y + (h0 - 8) / 2f + slide);
		g.enableScissor(tx, y, tx + tw, y + h0);
		if (showLyrics) {
			drawKaraoke(g, words, word, tx, ty, tw, ta, accent);
		} else {
			String fit = Gfx.fit(text, tw);
			if (status != null) {
				Gfx.text(g, fit, tx, ty, Gfx.alpha(0xFFE8E8F2, ta), false);
			} else {
				String tt = Gfx.fit(title, tw);
				Gfx.text(g, tt, tx, ty, Gfx.alpha(0xFFFFFFFF, ta), false);
				int w1 = Gfx.width(tt);
				if (!artist.isEmpty() && w1 < tw - 8) {
					Gfx.text(g, Gfx.fit("  ·  " + artist, tw - w1), tx + w1, ty, Gfx.alpha(0xFF9B9BB5, ta), false);
				}
			}
		}
		g.disableScissor();

		// equalizer
		float bx = x + width - barsW - pad + 3f;
		float by = y + h0 / 2f;
		double time = System.nanoTime() / 1.0e9;
		boolean playing = t.playing() || demo;
		for (int i = 0; i < bars.length; i++) {
			float target = playing
					? 0.25f + 0.35f * (float) Math.abs(Math.sin(time * (3.1 + i * 1.7) + i)) + 0.45f * beat * (i % 2 == 0 ? 1f : 0.7f)
					: 0.18f;
			bars[i] = Anim.approach(bars[i], Math.min(1f, target), 14f);
			float bh = Math.max(1.5f, bars[i] * h0 * 0.5f);
			int c = Gfx.lerp(Gfx.lerp(accent, 0xFFFFFFFF, 0.35f), accent, i / 3f);
			Gfx.roundRect(g, Math.round(bx + i * 3f), Math.round(by - bh / 2f), 2, Math.max(2, Math.round(bh)), 1, Gfx.alpha(c, a));
		}
	}

	private void drawKaraoke(GuiGraphicsExtractor g, String[] words, int word, int tx, int ty, int tw, float a, int accent) {
		int space = Gfx.width(" ");
		float[] xs = new float[words.length];
		float[] ws = new float[words.length];
		float cx = 0f;
		for (int i = 0; i < words.length; i++) {
			xs[i] = cx;
			ws[i] = Gfx.width(words[i]);
			cx += ws[i] + space;
		}
		float total = cx - space;
		float target = 0f;
		if (word >= 0 && total > tw) {
			float wanted = xs[word] + ws[word] / 2f - tw * 0.55f;
			target = Math.max(0f, Math.min(total - tw, wanted));
		}
		scroll = Anim.approach(scroll, target, 6f);
		float ox = tx - scroll;
		if (word >= 0) {
			float tX = xs[word] - 2.2f;
			float tW = ws[word] + 4.4f;
			if (hlW <= 0f) {
				hlX = tX;
				hlW = tW;
			}
			hlX = Anim.approach(hlX, tX, 16f);
			hlW = Anim.approach(hlW, tW, 16f);
			Gfx.roundRect(g, Math.round(ox + hlX), ty - 2, Math.round(hlW), 11, 3, Gfx.alpha(accent, a * 0.95f));
		}
		for (int i = 0; i < words.length; i++) {
			int col = i <= word ? 0xFFFFFFFF : 0xFFB9B9D6;
			Gfx.text(g, words[i], Math.round(ox + xs[i]), ty, Gfx.alpha(col, a), false);
		}
	}

	// ---------------------------------------------------------------- karta (klasyczna)

	private void drawCard(GuiGraphicsExtractor g, MediaService.Track t, boolean demo) {
		final int pad = 6;
		int cw = cardWidth.getInt();
		int inner = cw - pad * 2;
		boolean bar = progress.value;
		boolean text = lyrics.value;
		int lines = Math.max(1, lyricLines.getInt() | 1);

		int hgt = pad * 2 + 10 + 11;
		if (bar) hgt += 12;
		if (text) hgt += 6 + lines * 11;
		w = cw;
		h = hgt;

		Gfx.panel(g, x, y, w, h, 8, 1f);
		Gfx.roundRect(g, x + 4, y + 7, 2, h - 14, 1, Theme.accent());

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
			Lyrics.Result e = Lyrics.get(t);
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

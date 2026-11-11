package dev.nebula.client.module.impl;

import dev.nebula.client.media.LrcParser;
import dev.nebula.client.media.Lyrics;
import dev.nebula.client.media.MediaService;
import dev.nebula.client.media.WordTiming;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.render.Projector;
import dev.nebula.client.render.WorldOverlay;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.ui.Anim;
import dev.nebula.client.ui.Gfx;
import dev.nebula.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tekst piosenki ze Spotify wyswietlany na niebie. Uklad (kawalki 1-3 slow rozrzucone w rzedach, lekko przechylone,
 * pojawiajace sie slowo po slowie, plynne podazanie za kamera, wygaszanie poprzedniej linii) jest przeniesiony
 * z modu LyricsFlow (autor: 374m / cmonmanx, uzyte za jego zgoda) i dostosowany do renderowania Nebuli.
 * Tryb "Ekran" ustawia prosty tekst na gorze ekranu.
 */
public final class SkyLyricsModule extends Module implements WorldOverlay {
	private final ModeSetting mode = add(new ModeSetting("Mode", new String[]{"Niebo (świat)", "Ekran (góra)"}, 0));
	private final NumberSetting size = add(new NumberSetting("Rozmiar", 2, 12, 4, 0.5));
	private final NumberSetting lines = add(new NumberSetting("Linie tekstu (tryb Ekran)", 1, 5, 3, 2));
	private final NumberSetting elevation = add(new NumberSetting("Maks. wysokość nad horyzontem (°)", 5, 80, 45, 1));
	private final BoolSetting glow = add(new BoolSetting("Poświata", true));
	private final BoolSetting beatPulse = add(new BoolSetting("Pulsowanie przy słowach", true));

	private static final double DISTANCE = 60.0;
	private static final Random RND = new Random();

	private String trackKey = "";
	private int lastIndex = -2;
	private long lineStart;

	// tryb nieba
	private SkyLine active;
	private final List<SkyLine> exiting = new ArrayList<>();
	private double lastSide;
	private long introUntil;
	private int lastWord = -1;
	private float beat;

	public SkyLyricsModule() {
		super("skylyrics", "Tekst na niebie", "Tekst piosenki ze Spotify wyświetlany na niebie", Category.SPOTIFY, false);
	}

	@Override
	protected void onEnable() {
		MediaService.acquire();
	}

	@Override
	protected void onDisable() {
		MediaService.release();
		active = null;
		exiting.clear();
	}

	@Override
	public void drawWorld(GuiGraphicsExtractor g, Minecraft mc, Projector p) {
		MediaService.Track t = MediaService.current();
		if (t == null) return;
		Anim.frame();

		String key = t.artist() + "|" + t.title();
		if (!key.equals(trackKey)) {
			trackKey = key;
				lastIndex = -2;
			active = null;
				exiting.clear();
			introUntil = System.currentTimeMillis() + 3800L;
		}
		if (mode.index() == 0) drawSky(g, mc, p, t);
		else drawScreen(g, p, t);
	}

	// ---------------------------------------------------------------- niebo (algorytm z LyricsFlow)

	private void drawSky(GuiGraphicsExtractor g, Minecraft mc, Projector p, MediaService.Track t) {
		long now = System.currentTimeMillis();
		float dt = Anim.dt();
		double pos = t.position() + 0.1;

		Lyrics.Result res = Lyrics.get(t);
		SkyLine cur = null;
		String curKey = null;
		String[] words = null;
		double start = 0;
		double end = 0;
		boolean intro = false;

		if (res.state == Lyrics.State.SYNCED) {
			List<LrcParser.Line> ls = res.lines;
			int idx = LrcParser.indexAt(ls, pos);
			if (idx >= 0) {
				LrcParser.Line l = ls.get(idx);
				double next = idx + 1 < ls.size() ? ls.get(idx + 1).time() : l.time() + 5;
				start = l.time();
				end = Math.min(next, l.time() + 8);
				boolean blank = l.text().equals("...") || l.text().isBlank();
				if (!blank && pos <= end + 0.4) {
					words = WordTiming.split(l.text());
					curKey = "L" + idx;
				}
			}
		}
		if (words == null && now < introUntil) {
			// po zmianie piosenki (lub gdy nie ma tekstu) na chwile pokazujemy tytul
			words = WordTiming.split(t.title() + (t.artist().isEmpty() ? "" : " - " + t.artist()));
			curKey = "T" + trackKey;
			intro = true;
		}

		double dpp = Math.toDegrees((size.get() / 8.0) / DISTANCE);
		float pitch = p.pitchDeg();
		double targetEl = Math.min(elevation.get(), Math.max(-6.0, -pitch + 3.0));
		if (words != null && words.length > 0) {
			if (active == null || !active.key.equals(curKey)) {
				if (active != null) {
					active.exitAt = now;
					exiting.add(active);
				}
				active = new SkyLine(curKey, words, start, end, p.yawDeg(), targetEl, dpp, intro, now);
			}
		} else if (active != null) {
			active.exitAt = now;
			exiting.add(active);
			active = null;
		}
		exiting.removeIf(s -> now - s.exitAt > 520L);
		while (exiting.size() > 1) exiting.remove(0);

		int curWord = -1;
		if (active != null) {
			double ap = active.intro ? (now - active.born) / 1000.0 : pos;
			double as = active.intro ? 0 : active.start;
			double ae = active.intro ? 1.4 : active.end;
			curWord = WordTiming.wordAt(active.words, as, ae, ap);
			int wk = active.key.hashCode() * 31 + curWord;
			if (wk != lastWord) {
				lastWord = wk;
				beat = 1f;
			}
		}
		beat = Math.max(0f, beat - dt * 3.2f);

		for (SkyLine s : exiting) drawLine(g, p, s, -1, now, dt, dpp, targetEl);
		if (active != null) drawLine(g, p, active, curWord, now, dt, dpp, targetEl);
	}

	private void drawLine(GuiGraphicsExtractor g, Projector p, SkyLine s, int cur, long now, float dt, double dpp, double targetEl) {
		if (s.exitAt == 0L) {
			double dAz = angDiff(p.yawDeg(), s.baseAz);
			double kh = 1.0 - Math.exp(-3.0 * dt);
			if (Math.abs(dAz) > 26.0) s.baseAz += (dAz - Math.signum(dAz) * 26.0) * kh;
			double dEl = targetEl - s.baseEl;
			double kv = 1.0 - Math.exp(-(Math.abs(dEl) > 6.0 ? 4.0 : 1.5) * dt);
			s.baseEl += dEl * kv;
		}
		float exit = s.exitAt == 0L ? 0f : Anim.clamp01((now - s.exitAt) / 480f);
		float lineA = 1f - Anim.outCubic(exit);
		if (lineA <= 0.01f) return;

		int n = s.words.length;
		int upTo = s.exitAt != 0L ? n : cur;
		double pxPerBlock = (p.sh / 2.0) / (DISTANCE * p.tanHalf());
		float baseScale = (float) (size.get() * pxPerBlock / 8.0);
		int space = Gfx.width(" ");
		int accent = Theme.accent();
		int shadow = Gfx.lerp(0xFF000000 | accent, 0xFF0A0A14, 0.75f);
		long startDelay = !exiting.isEmpty() && s.exitAt == 0L ? 420L : 0L;
		double[] sc = new double[2];

		for (Chunk ch : s.chunks) {
			if (s.exitAt == 0L && ch.from > upTo) continue;
			double az = Math.toRadians(s.baseAz + ch.offAz);
			double el = Math.toRadians(s.baseEl + ch.offEl + Anim.outCubic(exit) * 3.0);
			double dx = -Math.sin(az) * Math.cos(el);
			double dy = Math.sin(el);
			double dz = Math.cos(az) * Math.cos(el);
			if (!p.toScreen(p.cx + dx * DISTANCE, p.cy + dy * DISTANCE, p.cz + dz * DISTANCE, sc)) continue;
			float kk = baseScale * ch.size;
			float rot = ch.roll + ch.yawTilt * 0.25f;
			float sx = kk * (float) Math.max(0.8, Math.cos(ch.yawTilt));

			Gfx.pushXform(g, (float) sc[0], (float) sc[1], rot, sx, kk);
			float lx = -ch.widthU / 2f;
			for (int i = ch.from; i < ch.to; i++) {
				String word = s.words[i];
				int ww = Gfx.width(word);
				boolean on = s.exitAt != 0L || i <= upTo;
				if (on && s.shown[i] == 0L) s.shown[i] = Math.max(now, s.born + startDelay);
				if (!on) {
					lx += ww + space;
					continue;
				}
				float e = Anim.outCubic((now - s.shown[i]) / 450f);
				float a = e * lineA;
				boolean current = s.exitAt == 0L && i == upTo;
				float bs = current && beatPulse.value ? 1f + 0.07f * beat : 1f;
				float wx = lx;
				float wy = -4.5f + (1f - e) * 3f;
				if (bs != 1f) {
					Gfx.pushXform(g, wx + ww / 2f, 0f, 0f, bs, bs);
					wx = -ww / 2f;
				}
				if (glow.value) {
					int gc = Gfx.alpha(accent, a * (current ? 0.16f : 0.09f));
					for (int layer = 3; layer >= 1; layer--) {
						Gfx.roundRect(g, Math.round(wx - 2 - layer * 2), Math.round(wy - 1 - layer * 2),
								ww + 4 + layer * 4, 10 + layer * 4, 4 + layer * 2, gc);
					}
				}
				Gfx.text(g, word, Math.round(wx), Math.round(wy + 1f), Gfx.alpha(shadow, a * 0.55f), false);
				int col = Gfx.lerp(0xFF000000 | accent, 0xFFFFFFFF, current ? 0.6f : 0.2f);
				Gfx.text(g, word, Math.round(wx), Math.round(wy), Gfx.alpha(col, a), false);
				if (bs != 1f) Gfx.popScale(g);
				lx += ww + space;
			}
			Gfx.popScale(g);
		}
	}

	private static double angDiff(double a, double b) {
		return ((a - b) % 360.0 + 540.0) % 360.0 - 180.0;
	}

	private static final class Chunk {
		int from;
		int to;
		float widthU;
		double offAz;
		double offEl;
		float yawTilt;
		float roll;
		float size = 1f;
	}

	/** Jedna linia tekstu rozbita na kawalki rozlozone w rzedach wokol kierunku patrzenia. */
	private final class SkyLine {
		final String key;
		final String[] words;
		final double start;
		final double end;
		final boolean intro;
		final long[] shown;
		final List<Chunk> chunks = new ArrayList<>();
		double baseAz;
		double baseEl;
		final long born;
		long exitAt;

		SkyLine(String key, String[] w, double start, double end, double az, double el, double dpp, boolean intro, long born) {
			this.key = key;
			this.words = w;
			this.start = start;
			this.end = end;
			this.intro = intro;
			this.born = born;
			int n = w.length;
			this.shown = new long[n];
			this.baseAz = az;
			this.baseEl = el;
			int space = Gfx.width(" ");

			int i = 0;
			while (i < n) {
				int j;
				int chars = w[i].length();
				for (j = i + 1; j < n && j - i < 3 && chars + w[j].length() <= (j - i == 1 ? 12 : 9); j++) chars += w[j].length();
				Chunk ch = new Chunk();
				ch.from = i;
				ch.to = j;
				for (int k = i; k < j; k++) ch.widthU += Gfx.width(w[k]) + (k > i ? space : 0);
				chunks.add(ch);
				i = j;
			}
			double gap = space * 2.4 * dpp;
			int cn = chunks.size();
			if (cn == 0) return;
			int rowsN = cn <= 2 ? 1 : (cn >= 6 && RND.nextFloat() < 0.35f ? 3 : 2);
			if (cn == 3 && RND.nextFloat() < 0.35f) rowsN = 1;
			List<List<Chunk>> rows = new ArrayList<>();
			int idx = 0;
			for (int r = 0; r < rowsN; r++) {
				int left = cn - idx;
				int rowsLeft = rowsN - r;
				int take = rowsLeft == 1 ? left
						: Math.max(1, Math.min(left - (rowsLeft - 1), Math.round((float) left / rowsLeft + (RND.nextFloat() - 0.5f) * 1.2f)));
				rows.add(new ArrayList<>(chunks.subList(idx, idx + take)));
				idx += take;
			}
			int big = RND.nextInt(cn);
			for (int k = 0; k < cn; k++) chunks.get(k).size = (k == big ? 1.22f : 0.86f) + RND.nextFloat() * 0.2f;

			double rowH = 17.0 * dpp;
			double side = (RND.nextDouble() * 2.0 - 1.0) * 20.0;
			if (Math.signum(side) == lastSide && RND.nextFloat() < 0.65f) side = -side;
			lastSide = Math.signum(side);
			double lift = 2.5 + RND.nextDouble() * 6.0;
			double stair = (RND.nextBoolean() ? 1 : -1) * (5.0 + RND.nextDouble() * 8.0);
			for (int r = 0; r < rows.size(); r++) {
				List<Chunk> rr = rows.get(r);
				double total = -gap;
				for (Chunk ch : rr) total += gap + ch.widthU * dpp * ch.size;
				if (total > 58.0) {
					float f = (float) (58.0 / total);
					for (Chunk ch : rr) ch.size *= f;
					total = 58.0;
				}
				double rowShift = side + r * stair + (RND.nextDouble() - 0.5) * 4.0;
				double maxShift = Math.max(0.0, 46.0 - total / 2.0);
				rowShift = Math.max(-maxShift, Math.min(maxShift, rowShift));
				double y = lift + (rows.size() - 1 - r) * rowH + (RND.nextDouble() - 0.5) * rowH * 0.15;
				double x = rowShift - total / 2.0;
				for (int k = 0; k < rr.size(); k++) {
					Chunk ch = rr.get(k);
					double cw = ch.widthU * dpp * ch.size;
					ch.offAz = x + cw / 2.0;
					ch.offEl = y + (k % 2 == 0 ? 0.8 : -0.6) + (RND.nextDouble() - 0.5) * 0.8;
					x += cw + gap;
					ch.yawTilt = (float) Math.toRadians((RND.nextFloat() < 0.5f ? -1 : 1) * (5.0f + RND.nextFloat() * 14.0f));
					ch.roll = (float) Math.toRadians((RND.nextFloat() - 0.5f) * 9.0f);
				}
			}
		}
	}

	// ---------------------------------------------------------------- tryb "Ekran"

	private void drawScreen(GuiGraphicsExtractor g, Projector p, MediaService.Track t) {
		int n = Math.max(1, lines.getInt() | 1);
		int centre = n / 2;
		String[] rows = new String[n];
		Lyrics.Result e = Lyrics.get(t);
		int idx = -1;
		switch (e.state) {
			case LOADING -> rows[centre] = "Szukam tekstu...";
			case NONE, PLAIN_ONLY, ERROR -> rows[centre] = t.title();
			case SYNCED -> {
				List<LrcParser.Line> ls = e.lines;
				idx = LrcParser.indexAt(ls, t.position() + 0.25);
				for (int i = 0; i < n; i++) {
					int li = idx + (i - centre);
					rows[i] = li >= 0 && li < ls.size() ? ls.get(li).text() : "";
				}
				if (idx == -1) rows[centre] = t.title() + " - " + t.artist();
			}
		}
		if (idx != lastIndex) {
			lastIndex = idx;
			lineStart = System.nanoTime();
		}
		float fade = Math.min(1f, (System.nanoTime() - lineStart) / 280_000_000f);
		float base = (float) (size.get() * 0.45);
		for (int i = 0; i < n; i++) {
			String text = rows[i];
			if (text == null || text.isEmpty()) continue;
			boolean cur = i == centre;
			float scale = cur ? base : base * 0.72f;
			double sx = p.sw / 2.0;
			double sy = p.sh * 0.12 + (i - centre) * 10.0 * base * 1.5 + 10 * base;
			float alpha = cur ? fade : Math.max(0.25f, 0.55f - 0.12f * Math.abs(i - centre));
			String fit = Gfx.fit(text, Math.max(20, (int) (p.sw * 0.92f / scale)));
			int tw = Gfx.width(fit);
			int px = Math.round((float) (sx / scale) - tw / 2f);
			int py = Math.round((float) (sy / scale) - 4f);
			Gfx.pushScale(g, scale);
			if (glow.value) {
				int gc = Gfx.alpha(Theme.accent(), 0.55f * alpha);
				Gfx.text(g, fit, px - 1, py, gc, false);
				Gfx.text(g, fit, px + 1, py, gc, false);
				Gfx.text(g, fit, px, py - 1, gc, false);
				Gfx.text(g, fit, px, py + 1, gc, false);
			}
			Gfx.text(g, fit, px, py, Gfx.alpha(0xFFFFFFFF, alpha), true);
			Gfx.popScale(g);
		}
	}
}

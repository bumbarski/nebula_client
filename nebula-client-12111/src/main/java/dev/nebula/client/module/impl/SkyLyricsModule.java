package dev.nebula.client.module.impl;

import dev.nebula.client.media.LrcParser;
import dev.nebula.client.media.Lyrics;
import dev.nebula.client.media.MediaService;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.render.Projector;
import dev.nebula.client.render.WorldOverlay;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.ui.Gfx;
import dev.nebula.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Tekst piosenki ze Spotify (z systemu Windows) wyswietlany na niebie: duze napisy zakotwiczone w swiecie nad
 * horyzontem, ktore przesuwaja sie razem z odtwarzaniem. Tryb "Ekran" ustawia je na gorze ekranu.
 */
public final class SkyLyricsModule extends Module implements WorldOverlay {
	private final ModeSetting mode = add(new ModeSetting("Tryb", new String[]{"Niebo (świat)", "Ekran (góra)"}, 0));
	private final NumberSetting size = add(new NumberSetting("Rozmiar", 2, 12, 5, 0.5));
	private final NumberSetting lines = add(new NumberSetting("Linie tekstu", 1, 5, 3, 2));
	private final NumberSetting elevation = add(new NumberSetting("Wysokość nad horyzontem (°)", 5, 80, 35, 1));
	private final BoolSetting glow = add(new BoolSetting("Poświata", true));

	private static final double DISTANCE = 60.0;

	private String trackKey = "";
	private float anchorYaw = Float.NaN;
	private int lastIndex = -2;
	private long lineStart;

	public SkyLyricsModule() {
		super("skylyrics", "Tekst na niebie", "Tekst piosenki ze Spotify wyświetlany na niebie", Category.VISUAL, false);
	}

	@Override
	protected void onEnable() {
		MediaService.acquire();
	}

	@Override
	protected void onDisable() {
		MediaService.release();
		anchorYaw = Float.NaN;
	}

	@Override
	public void drawWorld(GuiGraphics g, Minecraft mc, Projector p) {
		MediaService.Track t = MediaService.current();
		if (t == null) return;

		String key = t.artist() + "|" + t.title();
		if (!key.equals(trackKey)) {
			trackKey = key;
			anchorYaw = Float.NaN; // nowa piosenka: napisy pojawiaja sie tam, gdzie patrzysz
			lastIndex = -2;
		}
		if (Float.isNaN(anchorYaw)) anchorYaw = p.yawDeg();

		int n = Math.max(1, lines.getInt() | 1);
		int centre = n / 2;
		String[] rows = new String[n];

		Lyrics.Result e = Lyrics.get(t);
		int idx = -1;
		switch (e.state) {
			case LOADING -> rows[centre] = "Szukam tekstu...";
			case NONE -> rows[centre] = t.title();
			case PLAIN_ONLY -> rows[centre] = t.title();
			case ERROR -> rows[centre] = t.title();
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

		boolean sky = mode.index() == 0;
		double[] s = new double[2];
		double worldLine = size.get();
		double stepDeg = Math.toDegrees(Math.atan2(worldLine * 1.5, DISTANCE));
		double pxPerBlock = (p.sh / 2.0) / (DISTANCE * p.tanHalf());

		for (int i = 0; i < n; i++) {
			String text = rows[i];
			if (text == null || text.isEmpty()) continue;
			boolean cur = i == centre;
			double sx;
			double sy;
			float scale;
			if (sky) {
				double elev = Math.toRadians(elevation.get() - (i - centre) * stepDeg);
				double yaw = Math.toRadians(anchorYaw);
				double dx = -Math.sin(yaw) * Math.cos(elev);
				double dy = Math.sin(elev);
				double dz = Math.cos(yaw) * Math.cos(elev);
				if (!p.toScreen(p.cx + dx * DISTANCE, p.cy + dy * DISTANCE, p.cz + dz * DISTANCE, s)) continue;
				sx = s[0];
				sy = s[1];
				scale = (float) Math.max(0.3, Math.min(8.0, worldLine * pxPerBlock / 8.0));
			} else {
				scale = (float) (size.get() * 0.45);
				sx = p.sw / 2.0;
				sy = p.sh * 0.12 + (i - centre) * 10.0 * scale * 1.5 + 10 * scale;
			}
			if (!cur) scale *= 0.72f;

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

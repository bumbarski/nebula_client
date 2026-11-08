package dev.nebula.client.module.impl;

import dev.lyricsflow.config.ConfigManager;
import dev.lyricsflow.config.LFConfig;
import dev.lyricsflow.render.Themes;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Zakladka "Spotify" (tylko 1.21.11): ustawienia modu LyricsFlow (autor: 374m / cmonmanx, uzyty za jego zgoda),
 * ktory jest wbudowany w ten jar. Tu ustawiasz wszystko z menu Nebuli, a wartosci trafiaja do konfiguracji LyricsFlow.
 */
public final class LyricsFlowModule extends Module {
	private static final String[] THEME_IDS = Themes.ALL.stream().map(Themes.Theme::id).toArray(String[]::new);

	private final BoolSetting on = add(new BoolSetting("Włączone", true));
	private final BoolSetting lyrics = add(new BoolSetting("Tekst piosenki", true));
	private final ModeSetting style = add(new ModeSetting("Styl tekstu", new String[]{"Niebo", "Unoszący się"}, 0));
	private final NumberSetting scale = add(new NumberSetting("Rozmiar tekstu", 0.4, 2.0, 0.8, 0.05));
	private final NumberSetting opacity = add(new NumberSetting("Przezroczystość tekstu", 0.2, 1.0, 1.0, 0.05));
	private final BoolSetting glow = add(new BoolSetting("Poświata", true));
	private final BoolSetting beat = add(new BoolSetting("Pulsowanie przy słowach", false));
	private final BoolSetting caps = add(new BoolSetting("Wielkie litery", true));
	private final ModeSetting theme = add(new ModeSetting("Motyw kolorów", THEME_IDS, indexOf("lavender")));
	private final BoolSetting fx = add(new BoolSetting("Efekty nieba (kolory, zorza, gwiazdy)", false));
	private final BoolSetting bar = add(new BoolSetting("Pasek muzyki", true));
	private final ModeSetting barStyle = add(new ModeSetting("Styl paska", new String[]{"Karaoke", "Prosty"}, 0));
	private final NumberSetting barScale = add(new NumberSetting("Rozmiar paska", 0.6, 2.0, 1.0, 0.1));
	private final NumberSetting barY = add(new NumberSetting("Wysokość paska", 0.0, 0.5, 0.02, 0.01));
	private final BoolSetting clock = add(new BoolSetting("Zegar przy pasku", false));
	private final BoolSetting ping = add(new BoolSetting("Ping przy pasku", false));
	private final BoolSetting fps = add(new BoolSetting("FPS przy pasku", false));
	private final NumberSetting offset = add(new NumberSetting("Przesunięcie tekstu (ms)", -2000, 2000, 0, 50));

	private String applied = "";
	private int tickN;

	public LyricsFlowModule() {
		super("spotify", "Spotify i teksty", "Tekst piosenki na niebie i pasek muzyki (mod LyricsFlow, autor 374m)", Category.SPOTIFY, true);
		this.toggleable = false;
	}

	private static int indexOf(String id) {
		for (int i = 0; i < THEME_IDS.length; i++) if (THEME_IDS[i].equals(id)) return i;
		return 0;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (tickN++ % 40 == 0) freeKeys();
		LFConfig c;
		try {
			c = ConfigManager.get();
		} catch (Throwable t) {
			return;
		}
		if (c == null) return;
		String sig = on.value + "|" + lyrics.value + "|" + style.index() + "|" + scale.get() + "|" + opacity.get() + "|" + glow.value
				+ "|" + beat.value + "|" + caps.value + "|" + theme.index() + "|" + fx.value + "|" + bar.value + "|" + barStyle.index()
				+ "|" + barScale.get() + "|" + barY.get() + "|" + clock.value + "|" + ping.value + "|" + fps.value + "|" + offset.getInt();
		if (sig.equals(applied)) return;
		applied = sig;

		c.enabled = on.value;
		c.lyricsOn = lyrics.value;
		c.showLyrics = lyrics.value;
		c.lyricsStyle = style.index() == 0 ? LFConfig.LyricsStyle.SKY : LFConfig.LyricsStyle.FLOATING;
		c.lyricsScale = scale.getFloat();
		c.textOpacity = opacity.getFloat();
		c.glow = glow.value;
		c.beatGlow = beat.value;
		c.allCaps = caps.value;
		c.theme = THEME_IDS[theme.index()];
		c.customTheme = false;
		c.fxSkyColor = fx.value;
		c.fxWeather = fx.value;
		c.fxAurora = fx.value;
		c.fxStars = fx.value;
		c.stars = fx.value;
		c.streaks = fx.value;
		c.showTopBar = bar.value;
		c.topBarStyle = barStyle.index() == 0 ? LFConfig.TopBarStyle.KARAOKE : LFConfig.TopBarStyle.SIMPLE;
		c.topBarScale = barScale.getFloat();
		c.topBarY = barY.getFloat();
		c.clock = clock.value;
		c.ping = ping.value;
		c.fps = fps.value;
		c.syncOffsetMs = offset.getInt();
		try {
			ConfigManager.save();
		} catch (Throwable ignored) {
			// zapis konfiguracji LyricsFlow jest dodatkiem, ustawienia i tak dzialaja w tej sesji
		}
	}

	/** Skroty klawiszowe LyricsFlow (K, J, O, strzalki...) zostawiamy nieprzypisane: wszystko sterujesz z menu Nebuli. */
	private void freeKeys() {
		try {
			Class<?> keys = Class.forName("dev.lyricsflow.Keys");
			java.lang.reflect.Method all = keys.getMethod("all");
			Object arr = all.invoke(null);
			boolean changed = false;
			for (int i = 0; i < java.lang.reflect.Array.getLength(arr); i++) {
				Object k = java.lang.reflect.Array.get(arr, i);
				if (k instanceof KeyMapping km && !km.isUnbound()) {
					km.setKey(InputConstants.UNKNOWN);
					changed = true;
				}
			}
			if (changed) KeyMapping.resetMapping();
		} catch (Throwable ignored) {
			// brak klasy Keys = nic do odpinania
		}
	}
}

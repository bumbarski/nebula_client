package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.ui.Theme;

import java.awt.Color;

/** Kolor i grubosc obrysu bloku, na ktory patrzysz. */
public final class BlockOutlineModule extends Module {
	public static BlockOutlineModule instance;

	private final ModeSetting mode = add(new ModeSetting("Kolor", new String[]{"Motyw", "Tęcza", "Własny"}, 0));
	private final NumberSetting red = add(new NumberSetting("Czerwony", 0, 255, 90, 1));
	private final NumberSetting green = add(new NumberSetting("Zielony", 0, 255, 160, 1));
	private final NumberSetting blue = add(new NumberSetting("Niebieski", 0, 255, 255, 1));
	private final NumberSetting width = add(new NumberSetting("Grubość", 1, 8, 3, 0.5));

	public BlockOutlineModule() {
		super("blockoutline", "Obrys bloku", "Własny kolor i grubość obrysu bloku", Category.VISUAL, false);
		instance = this;
	}

	public int color() {
		return switch (mode.index()) {
			case 1 -> 0xFF000000 | Color.HSBtoRGB((System.currentTimeMillis() % 4000L) / 4000f, 0.75f, 1f);
			case 2 -> 0xFF000000 | (red.getInt() << 16) | (green.getInt() << 8) | blue.getInt();
			default -> 0xFF000000 | (Theme.accent() & 0xFFFFFF);
		};
	}

	public float width() {
		return width.getFloat();
	}
}

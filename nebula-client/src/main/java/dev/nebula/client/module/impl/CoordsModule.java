package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CoordsModule extends HudModule {
	private final BoolSetting direction = add(new BoolSetting("Pokaż kierunek", true));

	public CoordsModule() {
		super("coords", "Koordynaty", "Pozycja gracza i kierunek patrzenia", Category.HUD, true, 4, 104);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		double px = 0, py = 64, pz = 0;
		String facing = "Północ";
		if (mc.player != null) {
			px = mc.player.getX();
			py = mc.player.getY();
			pz = mc.player.getZ();
			facing = facingName(mc.player.getDirection());
		}

		List<String[]> rows = new ArrayList<>();
		rows.add(new String[]{"XYZ", String.format(Locale.ROOT, "%.1f %.1f %.1f", px, py, pz)});
		if (direction.value) {
			rows.add(new String[]{"Kier.", facing});
		}
		drawRows(g, rows);
	}

	private static String facingName(Direction d) {
		return switch (d) {
			case NORTH -> "Północ (-Z)";
			case SOUTH -> "Południe (+Z)";
			case EAST -> "Wschód (+X)";
			case WEST -> "Zachód (-X)";
			default -> "";
		};
	}
}

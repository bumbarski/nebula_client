package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PotionModule extends HudModule {
	public PotionModule() {
		super("potions", "Efekty", "Aktywne efekty mikstur z czasem do końca", Category.HUD, false, 10_000, 70);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		List<String[]> rows = new ArrayList<>();
		if (mc.player != null) {
			for (MobEffectInstance e : mc.player.getActiveEffects()) {
				String name = e.getEffect().value().getDisplayName().getString();
				int level = e.getAmplifier() + 1;
				String time = e.isInfiniteDuration() ? "∞" : format(e.getDuration() / 20);
				rows.add(new String[]{name + " " + level, time});
			}
		}

		if (rows.isEmpty()) {
			if (!preview) {
				hide();
				return;
			}
			rows.add(new String[]{"Efekty", "brak"});
		}
		drawRows(g, rows);
	}

	private static String format(int totalSeconds) {
		return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
	}
}

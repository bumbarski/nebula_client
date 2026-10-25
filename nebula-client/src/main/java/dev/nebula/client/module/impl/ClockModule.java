package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ClockModule extends HudModule {
	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("HH:mm:ss");

	private final BoolSetting seconds = add(new BoolSetting("Sekundy", false));

	public ClockModule() {
		super("clock", "Zegar", "Aktualna godzina systemowa", Category.HUD, false, 4, 84);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		String time = LocalTime.now().format(seconds.value ? LONG : SHORT);
		drawRows(g, List.<String[]>of(new String[]{"Czas", time}));
	}
}

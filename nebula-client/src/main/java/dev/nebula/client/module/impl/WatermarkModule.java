package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public final class WatermarkModule extends HudModule {
	public WatermarkModule() {
		super("watermark", "Znak wodny", "Nazwa klienta na ekranie", Category.VISUAL, true, 4, 4);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		drawRows(g, List.<String[]>of(new String[]{"Nebula", "0.1.0"}));
	}
}

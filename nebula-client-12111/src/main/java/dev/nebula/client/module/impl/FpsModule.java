package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public final class FpsModule extends HudModule {
	public FpsModule() {
		super("fps", "FPS", "Liczba klatek na sekundę", Category.HUD, true, 4, 24);
	}

	@Override
	public void draw(GuiGraphics g, Minecraft mc, boolean preview) {
		drawRows(g, List.<String[]>of(new String[]{"FPS", String.valueOf(mc.getFps())}));
	}
}

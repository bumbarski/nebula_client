package dev.nebula.client.hud;

import dev.nebula.client.NebulaClient;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.module.Module;
import dev.nebula.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudManager {
	private HudManager() {
	}

	public static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return;

		ModuleManager modules = NebulaClient.modules();
		for (Module m : modules.all()) {
			if (m.isEnabled()) m.onFrame(mc);
		}

		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		for (HudModule hud : modules.hud()) {
			if (!hud.isEnabled()) continue;
			clamp(hud, sw, sh);
			hud.draw(g, mc, false);
		}
	}

	/** Trzyma element w granicach ekranu (dziala tez po zmianie rozmiaru okna). */
	public static void clamp(HudModule hud, int sw, int sh) {
		hud.x = Math.max(0, Math.min(hud.x, Math.max(0, sw - hud.w)));
		hud.y = Math.max(0, Math.min(hud.y, Math.max(0, sh - hud.h)));
	}
}

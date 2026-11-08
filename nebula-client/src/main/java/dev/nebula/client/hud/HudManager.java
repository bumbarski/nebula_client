package dev.nebula.client.hud;

import dev.nebula.client.NebulaClient;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.module.Module;
import dev.nebula.client.module.ModuleManager;
import dev.nebula.client.render.Projector;
import dev.nebula.client.render.WorldOverlay;
import dev.nebula.client.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudManager {
	private HudManager() {
	}

	public static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		// Po F1 (ukryty interfejs) vanilla w ogole nie rysuje HUD, wiec osobny warunek nie jest potrzebny.
		if (mc.player == null) return;

		ModuleManager modules = NebulaClient.modules();
		for (Module m : modules.all()) {
			if (m.isEnabled()) m.onFrame(mc);
		}

		// nakladki swiata (ESP, sus chunk) pod elementami HUD
		Projector projector = null;
		for (Module m : modules.all()) {
			if (m.isEnabled() && m instanceof WorldOverlay overlay) {
				if (projector == null) projector = Projector.create(mc);
				overlay.drawWorld(g, mc, projector);
			}
		}

		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		for (HudModule hud : modules.hud()) {
			if (!hud.isEnabled()) continue;
			clamp(hud, sw, sh);
			drawScaled(g, mc, hud, false);
		}

		// menu Nebuli jako panel w swiecie (tryb Vision Pro)
		dev.nebula.client.ui.WorldPanel.render(g, mc);
	}

	/** Rysuje element z jego skala; w and h po wywolaniu sa juz po przeskalowaniu (do przesuwania w edytorze). */
	public static void drawScaled(GuiGraphicsExtractor g, Minecraft mc, HudModule hud, boolean preview) {
		float s = hud.scale.getFloat();
		if (Math.abs(s - 1f) < 0.001f) {
			hud.draw(g, mc, preview);
			return;
		}
		int ox = hud.x;
		int oy = hud.y;
		hud.x = Math.round(ox / s);
		hud.y = Math.round(oy / s);
		Gfx.pushScale(g, s);
		hud.draw(g, mc, preview);
		Gfx.popScale(g);
		hud.x = ox;
		hud.y = oy;
		hud.w = Math.round(hud.w * s);
		hud.h = Math.round(hud.h * s);
	}

	/** Trzyma element w granicach ekranu (dziala tez po zmianie rozmiaru okna). */
	public static void clamp(HudModule hud, int sw, int sh) {
		hud.x = Math.max(0, Math.min(hud.x, Math.max(0, sw - hud.w)));
		hud.y = Math.max(0, Math.min(hud.y, Math.max(0, sh - hud.h)));
	}
}

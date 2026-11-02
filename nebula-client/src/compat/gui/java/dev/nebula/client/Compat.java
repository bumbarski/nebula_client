package dev.nebula.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;

/** Roznice w API miedzy wersjami gry: 26.2 trzyma ekran w mc.gui. */
public final class Compat {
	private Compat() {
	}

	public static Vec3 cameraPos(Minecraft mc) {
		return mc.gameRenderer.mainCamera().position();
	}

	public static float cameraYaw(Minecraft mc) {
		return mc.gameRenderer.mainCamera().yRot();
	}

	public static float cameraPitch(Minecraft mc) {
		return mc.gameRenderer.mainCamera().xRot();
	}

	public static Screen screen(Minecraft mc) {
		return mc.gui.screen();
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		mc.gui.setScreen(screen);
	}
}

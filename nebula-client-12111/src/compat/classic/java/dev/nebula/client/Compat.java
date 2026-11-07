package dev.nebula.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;

/** Roznice w API miedzy wersjami gry: 26.1 i starsze trzymaja ekran bezposrednio w Minecraft. */
public final class Compat {
	private Compat() {
	}

	public static Vec3 cameraPos(Minecraft mc) {
		return mc.gameRenderer.getMainCamera().position();
	}

	public static float cameraYaw(Minecraft mc) {
		return mc.gameRenderer.getMainCamera().yRot();
	}

	public static float cameraPitch(Minecraft mc) {
		return mc.gameRenderer.getMainCamera().xRot();
	}

	public static Screen screen(Minecraft mc) {
		return mc.screen;
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		mc.setScreen(screen);
	}
}

package dev.nebula.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Roznice w API miedzy wersjami gry: 26.1 i starsze trzymaja ekran bezposrednio w Minecraft. */
public final class Compat {
	private Compat() {
	}

	public static Screen screen(Minecraft mc) {
		return mc.screen;
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		mc.setScreen(screen);
	}
}

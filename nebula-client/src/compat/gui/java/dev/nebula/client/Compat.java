package dev.nebula.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Roznice w API miedzy wersjami gry: 26.2 trzyma ekran w mc.gui. */
public final class Compat {
	private Compat() {
	}

	public static Screen screen(Minecraft mc) {
		return mc.gui.screen();
	}

	public static void setScreen(Minecraft mc, Screen screen) {
		mc.gui.setScreen(screen);
	}
}

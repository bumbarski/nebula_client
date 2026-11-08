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

	/** Shift-klik w slot kontenera (nazwy API roznia sie miedzy wersjami, wiec przez refleksje). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void quickMove(Minecraft mc, int containerId, int slot) {
		try {
			Class<?> type;
			try {
				type = Class.forName("net.minecraft.world.inventory.ContainerInput");
			} catch (ClassNotFoundException e) {
				type = Class.forName("net.minecraft.world.inventory.ClickType");
			}
			Object quick = Enum.valueOf((Class) type, "QUICK_MOVE");
			for (java.lang.reflect.Method m : mc.gameMode.getClass().getMethods()) {
				Class<?>[] pt = m.getParameterTypes();
				if (pt.length == 5 && pt[3] == type && (m.getName().equals("handleInventoryMouseClick") || m.getName().equals("handleContainerInput"))) {
					m.invoke(mc.gameMode, containerId, slot, 0, quick, mc.player);
					return;
				}
			}
		} catch (Throwable ignored) {
		}
	}
}

package dev.nebula.client;

import net.minecraft.client.Minecraft;

/** Male pomocniki refleksyjne dla API, ktore zmienia sie miedzy wersjami Minecrafta. */
public final class Reflect {
	private Reflect() {
	}

	/** Klik w kontenerze danego typu (QUICK_MOVE, SWAP...). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void click(Minecraft mc, int containerId, int slot, int button, String type) {
		try {
			Class<?> t;
			try {
				t = Class.forName("net.minecraft.world.inventory.ContainerInput");
			} catch (ClassNotFoundException e) {
				t = Class.forName("net.minecraft.world.inventory.ClickType");
			}
			Object v = Enum.valueOf((Class) t, type);
			for (java.lang.reflect.Method m : mc.gameMode.getClass().getMethods()) {
				Class<?>[] pt = m.getParameterTypes();
				if (pt.length == 5 && pt[3] == t && (m.getName().equals("handleInventoryMouseClick") || m.getName().equals("handleContainerInput"))) {
					m.invoke(mc.gameMode, containerId, slot, button, v, mc.player);
					return;
				}
			}
		} catch (Throwable ignored) {
		}
	}

	public static void selectSlot(Minecraft mc, int slot) {
		Object inv = mc.player.getInventory();
		try {
			inv.getClass().getMethod("setSelectedSlot", int.class).invoke(inv, slot);
			return;
		} catch (Throwable ignored) {
		}
		try {
			java.lang.reflect.Field f = inv.getClass().getDeclaredField("selected");
			f.setAccessible(true);
			f.setInt(inv, slot);
		} catch (Throwable ignored) {
		}
	}

	public static int selectedSlot(Minecraft mc) {
		Object inv = mc.player.getInventory();
		try {
			return (int) inv.getClass().getMethod("getSelectedSlot").invoke(inv);
		} catch (Throwable ignored) {
		}
		try {
			java.lang.reflect.Field f = inv.getClass().getDeclaredField("selected");
			f.setAccessible(true);
			return f.getInt(inv);
		} catch (Throwable ignored) {
			return 0;
		}
	}

	public static void setIntField(Object o, String name, int v) {
		for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
			try {
				java.lang.reflect.Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				f.setInt(o, v);
				return;
			} catch (Throwable ignored) {
			}
		}
	}
}

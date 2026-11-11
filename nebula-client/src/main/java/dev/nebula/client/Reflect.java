package dev.nebula.client;

import net.minecraft.client.Minecraft;

/** Male pomocniki refleksyjne dla API, ktore zmienia sie miedzy wersjami Minecrafta. */
public final class Reflect {
	private Reflect() {
	}

	/** Klik w kontenerze danego typu (QUICK_MOVE, SWAP, PICKUP...). Szuka metody po sygnaturze (int,int,int,enum,Player), niezaleznie od nazwy. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void click(Minecraft mc, int containerId, int slot, int button, String type) {
		try {
			for (java.lang.reflect.Method m : mc.gameMode.getClass().getMethods()) {
				Class<?>[] pt = m.getParameterTypes();
				if (pt.length != 5 || pt[0] != int.class || pt[1] != int.class || pt[2] != int.class || !pt[3].isEnum()) continue;
				if (!pt[4].isAssignableFrom(mc.player.getClass())) continue;
				Object v = Enum.valueOf((Class) pt[3], type);
				m.invoke(mc.gameMode, containerId, slot, button, v, mc.player);
				return;
			}
			System.err.println("[Nebula] Nie znaleziono metody klikania w kontenerze");
		} catch (Throwable t) {
			System.err.println("[Nebula] click: " + t);
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

	/** Kod klawisza przypisanego do KeyMapping (przez zapis "key.keyboard.w"). */
	public static int keyCode(net.minecraft.client.KeyMapping k) {
		try {
			Object key = com.mojang.blaze3d.platform.InputConstants.class.getMethod("getKey", String.class).invoke(null, k.saveString());
			return (int) key.getClass().getMethod("getValue").invoke(key);
		} catch (Throwable ignored) {
			return -1;
		}
	}

	public static boolean keyDown(Minecraft mc, int code) {
		try {
			Object window = mc.getWindow();
			for (java.lang.reflect.Method m : com.mojang.blaze3d.platform.InputConstants.class.getMethods()) {
				if (!m.getName().equals("isKeyDown") || m.getParameterCount() != 2) continue;
				Class<?> pt = m.getParameterTypes()[0];
				Object arg = window;
				if (pt == long.class) {
					try {
						arg = window.getClass().getMethod("handle").invoke(window);
					} catch (NoSuchMethodException e) {
						arg = window.getClass().getMethod("getWindow").invoke(window);
					}
				}
				return (boolean) m.invoke(null, arg, code);
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/** Wysyla pakiet pozycji (x, y+dy, z, onGround=false); konstruktor zalezy od wersji, wiec przez refleksje. */
	public static void sendPos(Minecraft mc, double dy) {
		try {
			Class<?> c = Class.forName("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Pos");
			for (java.lang.reflect.Constructor<?> k : c.getConstructors()) {
				int n = k.getParameterCount();
				if (n == 4) {
					mc.getConnection().send((net.minecraft.network.protocol.Packet<?>) k.newInstance(mc.player.getX(), mc.player.getY() + dy, mc.player.getZ(), false));
					return;
				}
				if (n == 5) {
					mc.getConnection().send((net.minecraft.network.protocol.Packet<?>) k.newInstance(mc.player.getX(), mc.player.getY() + dy, mc.player.getZ(), false, false));
					return;
				}
			}
		} catch (Throwable ignored) {
		}
	}
}

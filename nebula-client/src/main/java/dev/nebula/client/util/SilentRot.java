package dev.nebula.client.util;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** Ciche rotacje: podmienia kat w pakietach ruchu wysylanych na serwer, a kamera gracza zostaje bez zmian. */
public final class SilentRot {
	private SilentRot() {
	}

	public static boolean on;
	public static float yaw, pitch;

	public static void set(float y, float p) {
		on = true;
		yaw = y;
		pitch = p;
	}

	public static void clear() {
		on = false;
	}

	public static Packet<?> apply(Packet<?> pkt) {
		if (!on || !(pkt instanceof ServerboundMovePlayerPacket m)) return pkt;
		try {
			boolean og = m.isOnGround();
			if (m.hasPosition()) {
				for (var k : ServerboundMovePlayerPacket.PosRot.class.getConstructors()) {
					int n = k.getParameterCount();
					if (n == 6) return (Packet<?>) k.newInstance(m.getX(0), m.getY(0), m.getZ(0), yaw, pitch, og);
					if (n == 7) return (Packet<?>) k.newInstance(m.getX(0), m.getY(0), m.getZ(0), yaw, pitch, og, false);
				}
			} else {
				for (var k : ServerboundMovePlayerPacket.Rot.class.getConstructors()) {
					int n = k.getParameterCount();
					if (n == 3) return (Packet<?>) k.newInstance(yaw, pitch, og);
					if (n == 4) return (Packet<?>) k.newInstance(yaw, pitch, og, false);
				}
			}
		} catch (Throwable ignored) {
		}
		return pkt;
	}

	/** Wysyla pakiet samej rotacji (do synchronizacji tuz przed atakiem). */
	public static void sendRot(net.minecraft.client.Minecraft mc, float y, float p) {
		try {
			for (var k : ServerboundMovePlayerPacket.Rot.class.getConstructors()) {
				int n = k.getParameterCount();
				if (n == 3) {
					mc.getConnection().send((Packet<?>) k.newInstance(y, p, mc.player.onGround()));
					return;
				}
				if (n == 4) {
					mc.getConnection().send((Packet<?>) k.newInstance(y, p, mc.player.onGround(), false));
					return;
				}
			}
		} catch (Throwable ignored) {
		}
	}
}

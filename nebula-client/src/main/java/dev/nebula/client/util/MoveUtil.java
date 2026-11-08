package dev.nebula.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;

/** Pomocnicze funkcje ruchu przeniesione z ThunderHack Recode (MovementUtility, GPL-3.0, autor pan4ur). */
public final class MoveUtil {
	private MoveUtil() {
	}

	public static float forward() {
		var o = Minecraft.getInstance().options;
		return (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
	}

	public static float strafe() {
		var o = Minecraft.getInstance().options;
		return (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
	}

	public static boolean isMoving() {
		return forward() != 0 || strafe() != 0;
	}

	public static double[] forward(double d) {
		Minecraft mc = Minecraft.getInstance();
		float f = forward();
		float f2 = strafe();
		float f3 = mc.player.getYRot();
		if (f != 0.0f) {
			if (f2 > 0.0f) f3 += (f > 0.0f) ? -45 : 45;
			else if (f2 < 0.0f) f3 += (f > 0.0f) ? 45 : -45;
			f2 = 0.0f;
			f = f > 0.0f ? 1.0f : -1.0f;
		}
		double d2 = Math.sin(Math.toRadians(f3 + 90.0f));
		double d3 = Math.cos(Math.toRadians(f3 + 90.0f));
		return new double[]{f * d * d3 + f2 * d * d2, f * d * d2 - f2 * d * d3};
	}

	/** Poziom efektu (0 = I) albo -1; nazwy efektow roznia sie miedzy wersjami, wiec przez refleksje. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static int amp(String... names) {
		var p = Minecraft.getInstance().player;
		if (p == null) return -1;
		for (String n : names) {
			try {
				Object holder = MobEffects.class.getField(n).get(null);
				var inst = p.getEffect((net.minecraft.core.Holder) holder);
				return inst == null ? -1 : inst.getAmplifier();
			} catch (Throwable ignored) {
			}
		}
		return -1;
	}

	public static int speedAmp() {
		return amp("SPEED", "MOVEMENT_SPEED");
	}

	public static int slowAmp() {
		return amp("SLOWNESS", "MOVEMENT_SLOWDOWN");
	}

	public static int jumpAmp() {
		return amp("JUMP_BOOST", "JUMP");
	}

	public static double jumpSpeed() {
		Minecraft mc = Minecraft.getInstance();
		double jump = 0.3999999463558197;
		int e = jumpAmp();
		if (e >= 0) jump += (e + 1) * 0.1;
		return jump;
	}

	public static double baseMoveSpeed() {
		Minecraft mc = Minecraft.getInstance();
		double d = 0.2873;
		if (mc.player == null) return d;
		if (speedAmp() >= 0) d *= 1.0 + 0.2 * (speedAmp() + 1);
		if (jumpAmp() >= 0) d /= 1.0 + 0.2 * (jumpAmp() + 1);
		if (slowAmp() >= 0) d /= 1.0 + (0.2 * (slowAmp() + 1));
		return d;
	}

	/** Predkosc gracza w ostatnim ticku (blok/tick). */
	public static float currentSpeed() {
		Minecraft mc = Minecraft.getInstance();
		return (float) Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo);
	}
}

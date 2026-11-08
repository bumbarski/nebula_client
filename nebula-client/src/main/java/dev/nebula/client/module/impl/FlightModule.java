package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Latanie: ruch w kierunku patrzenia (W/S/A/D), spacja w gore, shift w dol. */
public final class FlightModule extends Module {
	private final NumberSetting speed = add(new NumberSetting("Prędkość", 0.1, 3.0, 0.8, 0.05));
	private final NumberSetting vertical = add(new NumberSetting("Prędkość pionowa", 0.1, 2.0, 0.6, 0.05));

	public FlightModule() {
		super("flight", "Latanie", "Lataj jak w trybie kreatywnym (spacja w górę, shift w dół)", Category.MOVEMENT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || Compat.screen(mc) != null) return;
		double yaw = Math.toRadians(mc.player.getYRot());
		double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
		double s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
		double sp = speed.get();
		double mx = (-Math.sin(yaw) * f + Math.cos(yaw) * s) * sp;
		double mz = (Math.cos(yaw) * f + Math.sin(yaw) * s) * sp;
		double my = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
		mc.player.setDeltaMovement(new Vec3(mx, my * vertical.get(), mz));
		mc.player.fallDistance = 0;
	}
}

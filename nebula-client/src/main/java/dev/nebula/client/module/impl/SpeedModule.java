package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Szybsze bieganie po ziemi (mnozy poziomy ped podczas chodzenia). */
public final class SpeedModule extends Module {
	private final NumberSetting speed = add(new NumberSetting("Prędkość", 0.15, 1.0, 0.35, 0.01));

	public SpeedModule() {
		super("speed", "Szybkość", "Szybsze bieganie", Category.MOVEMENT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.screen != null) return;
		double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
		double s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
		if (f == 0 && s == 0) return;
		double yaw = Math.toRadians(mc.player.getYRot());
		double len = Math.sqrt(f * f + s * s);
		f /= len;
		s /= len;
		double sp = speed.get();
		Vec3 d = mc.player.getDeltaMovement();
		mc.player.setDeltaMovement(new Vec3((-Math.sin(yaw) * f + Math.cos(yaw) * s) * sp, d.y, (Math.cos(yaw) * f + Math.sin(yaw) * s) * sp));
	}
}

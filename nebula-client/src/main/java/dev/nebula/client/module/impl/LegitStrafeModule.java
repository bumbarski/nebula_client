package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Lepsza kontrola w powietrzu: lekkie przyspieszenie w bok i do przodu podczas skoku. */
public final class LegitStrafeModule extends Module {
	private final NumberSetting power = add(new NumberSetting("Power", 0.005, 0.05, 0.02, 0.005));

	public LegitStrafeModule() {
		super("legitstrafe", "Legit Strafe", "Better air control", Category.MOVEMENT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.player.onGround() || Compat.screen(mc) != null) return;
		double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
		double s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
		if (f == 0 && s == 0) return;
		double yaw = Math.toRadians(mc.player.getYRot());
		double p = power.get();
		Vec3 d = mc.player.getDeltaMovement();
		mc.player.setDeltaMovement(d.x + (-Math.sin(yaw) * f + Math.cos(yaw) * s) * p, d.y, d.z + (Math.cos(yaw) * f + Math.sin(yaw) * s) * p);
	}
}

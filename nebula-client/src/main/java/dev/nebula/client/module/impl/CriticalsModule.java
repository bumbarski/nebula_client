package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Przy ataku stojac na ziemi podskakuje, zeby trafienie bylo krytyczne. */
public final class CriticalsModule extends Module {
	public CriticalsModule() {
		super("criticals", "Criticals", "Podskakuje przy ataku, by zadać krytyka", Category.COMBAT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || Compat.screen(mc) != null) return;
		if (mc.options.keyAttack.isDown() && mc.crosshairPickEntity != null && mc.player.onGround()) {
			Vec3 d = mc.player.getDeltaMovement();
			mc.player.setDeltaMovement(d.x, 0.42, d.z);
		}
	}
}

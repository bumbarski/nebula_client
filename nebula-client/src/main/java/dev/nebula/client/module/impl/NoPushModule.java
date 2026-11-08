package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Brak odpychania przez innych graczy: cofamy ruch spowodowany kolizja z nimi. */
public final class NoPushModule extends Module {
	private Vec3 last = Vec3.ZERO;

	public NoPushModule() {
		super("nopush", "No Push", "Gracze i moby nie odpychają cię", Category.MOVEMENT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		boolean touching = false;
		for (var e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(0.1))) {
			if (e instanceof net.minecraft.world.entity.LivingEntity || e instanceof Player) {
				touching = true;
				break;
			}
		}
		Vec3 d = mc.player.getDeltaMovement();
		if (touching && mc.options.keyUp.isDown() == false && mc.options.keyDown.isDown() == false
				&& mc.options.keyLeft.isDown() == false && mc.options.keyRight.isDown() == false) {
			mc.player.setDeltaMovement(new Vec3(0, d.y, 0));
		}
		last = d;
	}
}

package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Aura: automatycznie bije najblizszy cel w zasiegu, gdy pasek ataku jest naladowany. */
public final class KillAuraModule extends Module {
	private final NumberSetting range = add(new NumberSetting("Zasięg", 2.0, 6.0, 3.5, 0.1));
	private final BoolSetting players = add(new BoolSetting("Tylko gracze", true));
	private final BoolSetting rotate = add(new BoolSetting("Celuj kamerą", true));
	private final BoolSetting walls = add(new BoolSetting("Przez ściany", false));
	private final NumberSetting charge = add(new NumberSetting("Naładowanie ataku", 0.5, 1.0, 0.95, 0.05));

	public KillAuraModule() {
		super("killaura", "Aura", "Automatycznie atakuje najbliższy cel w zasięgu", Category.COMBAT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.gameMode == null || Compat.screen(mc) != null) return;
		double r = range.get();
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(r + 1))) {
			if (!(e instanceof LivingEntity le) || !le.isAlive() || e == mc.player) continue;
			if (players.value && !(e instanceof Player)) continue;
			double d = mc.player.distanceTo(e);
			if (d > r || d >= bestD) continue;
			if (!walls.value && !mc.player.hasLineOfSight(e)) continue;
			best = e;
			bestD = d;
		}
		if (best == null) return;
		if (rotate.value) {
			Vec3 eye = mc.player.getEyePosition();
			Vec3 t = best.getBoundingBox().getCenter();
			double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
			double h = Math.sqrt(dx * dx + dz * dz);
			mc.player.setYRot((float) Math.toDegrees(Math.atan2(dz, dx)) - 90f);
			mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, h)));
		}
		if (mc.player.getAttackStrengthScale(0f) >= charge.getFloat()) {
			mc.gameMode.attack(mc.player, best);
			mc.player.swing(InteractionHand.MAIN_HAND);
		}
	}
}

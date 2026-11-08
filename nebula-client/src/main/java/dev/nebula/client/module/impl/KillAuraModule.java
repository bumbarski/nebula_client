package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Aura — logika przeniesiona z ThunderHack Recode (Aura.java, GPL-3.0, autor pan4ur):
 * wybor celu (Sort, LockTarget), kolejnosc krytyka (SmartCrit), cooldown ataku, opoznienie (AttackTickLimit / OldDelay),
 * rotacje typu Track z krokami yaw/pitch i "DVD-point" w hitboxie, DropSprint/ReturnSprint, AutoWeapon.
 * Uproszczenia: rotacje sa widoczne po stronie klienta (brak silent rotation), bez Resolvera i trybow Grim/Interact.
 */
public final class KillAuraModule extends Module {
	private final NumberSetting attackRange = add(new NumberSetting("Zasięg", 1.0, 6.0, 3.1, 0.1));
	private final NumberSetting wallRange = add(new NumberSetting("Zasięg przez ściany", 0.0, 6.0, 3.1, 0.1));
	private final NumberSetting fov = add(new NumberSetting("FOV", 1, 180, 180, 1));
	private final ModeSetting rotationMode = add(new ModeSetting("Rotacje", new String[]{"Track", "None"}, 0));
	private final ModeSetting sort = add(new ModeSetting("Sortowanie", new String[]{"Najbliższy", "Najdalszy", "FOV", "Najmniej HP", "Najwięcej HP"}, 0));
	private final BoolSetting lockTarget = add(new BoolSetting("Blokuj cel", true));
	private final ModeSetting weapon = add(new ModeSetting("Auto broń", new String[]{"Wyłączone", "Normalnie"}, 0));
	private final BoolSetting onlyWeapon = add(new BoolSetting("Tylko z bronią", false));
	private final BoolSetting smartCrit = add(new BoolSetting("SmartCrit", true));
	private final BoolSetting onlyCrit = add(new BoolSetting("Tylko krytyki", false));
	private final BoolSetting autoJump = add(new BoolSetting("AutoJump", false));
	private final BoolSetting pauseWhileEating = add(new BoolSetting("Pauza przy jedzeniu", false));
	private final BoolSetting pauseInInventory = add(new BoolSetting("Pauza w ekwipunku", true));
	private final BoolSetting dropSprint = add(new BoolSetting("DropSprint", true));
	private final BoolSetting returnSprint = add(new BoolSetting("ReturnSprint", true));
	private final BoolSetting oldDelay = add(new BoolSetting("OldDelay (CPS)", false));
	private final NumberSetting minCps = add(new NumberSetting("Min CPS", 1, 20, 7, 1));
	private final NumberSetting maxCps = add(new NumberSetting("Max CPS", 1, 20, 12, 1));
	private final NumberSetting attackCooldown = add(new NumberSetting("Cooldown ataku", 0.5, 1.0, 0.9, 0.05));
	private final NumberSetting attackBaseTime = add(new NumberSetting("Bazowy czas ataku", 0.0, 2.0, 0.5, 0.05));
	private final NumberSetting attackTickLimit = add(new NumberSetting("Limit ticków ataku", 0, 20, 11, 1));
	private final NumberSetting critFall = add(new NumberSetting("Dystans spadku krytyka", 0.0, 1.0, 0.0, 0.05));
	private final NumberSetting aimRange = add(new NumberSetting("Zasięg celowania", 0.0, 6.0, 3.1, 0.1));
	private final NumberSetting minYaw = add(new NumberSetting("Min krok yaw", 1, 180, 65, 1));
	private final NumberSetting maxYaw = add(new NumberSetting("Max krok yaw", 1, 180, 75, 1));
	private final NumberSetting maxPitchStep = add(new NumberSetting("Max krok pitch", 1, 90, 8, 1));
	private final NumberSetting aimedPitchStep = add(new NumberSetting("Krok pitch (na celu)", 0, 90, 1, 0.5));
	private final NumberSetting pitchAccelerate = add(new NumberSetting("Przyspieszenie pitch", 1, 10, 1.65, 0.05));
	private final BoolSetting players = add(new BoolSetting("Gracze", true));
	private final BoolSetting mobs = add(new BoolSetting("Moby", true));
	private final BoolSetting animals = add(new BoolSetting("Zwierzęta", true));
	private final BoolSetting villagers = add(new BoolSetting("Mieszkańcy", true));
	private final BoolSetting hostiles = add(new BoolSetting("Wrogie", true));
	private final BoolSetting ignoreInvisible = add(new BoolSetting("Ignoruj niewidzialne", false));
	private final BoolSetting ignoreNamed = add(new BoolSetting("Ignoruj nazwane", false));
	private final BoolSetting ignoreCreative = add(new BoolSetting("Ignoruj tryb kreatywny", true));

	private Entity target;
	private float rotYaw, rotPitch, pitchAcc = 1f;
	private Vec3 rotPoint = Vec3.ZERO;
	private Vec3 rotMotion = Vec3.ZERO;
	private int hitTicks;
	private boolean lookingAtHitbox;

	public KillAuraModule() {
		super("killaura", "Aura", "Automatyczny atak (logika z ThunderHack Recode)", Category.COMBAT, false);
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			rotYaw = mc.player.getYRot();
			rotPitch = mc.player.getXRot();
		}
	}

	@Override
	protected void onDisable() {
		target = null;
	}

	private static float rnd(float a, float b) {
		return a >= b ? a : (float) ThreadLocalRandom.current().nextDouble(a, b);
	}

	private boolean haveWeapon(Minecraft mc) {
		if (!onlyWeapon.value) return true;
		ItemStack h = mc.player.getMainHandItem();
		if (weapon.index() == 0) return h.is(ItemTags.SWORDS) || h.is(ItemTags.AXES);
		for (int i = 0; i < 9; i++) {
			ItemStack st = mc.player.getInventory().getItem(i);
			if (st.is(ItemTags.SWORDS) || st.is(ItemTags.AXES)) return true;
		}
		return false;
	}

	@Override
	public void onTick(Minecraft mc) {
		var p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null) return;
		if (hitTicks > 0) hitTicks--;
		if (Compat.screen(mc) != null) return;
		if (p.isUsingItem() && pauseWhileEating.value) return;
		if (!haveWeapon(mc)) {
			target = null;
			return;
		}
		updateTarget(mc);
		if (target == null) return;

		if (!mc.options.keyJump.isDown() && p.onGround() && autoJump.value) {
			Vec3 d = p.getDeltaMovement();
			p.setDeltaMovement(d.x, 0.42, d.z);
		}

		boolean crit = autoCrit(mc);
		calcRotations(mc);
		boolean ready = crit && (lookingAtHitbox || rotationMode.index() == 1);
		if (!ready) return;

		boolean sprint = p.isSprinting();
		if (sprint && dropSprint.value) p.setSprinting(false);
		int prev = -1;
		if (weapon.index() == 1) {
			for (int i = 0; i < 9; i++) {
				if (p.getInventory().getItem(i).is(ItemTags.SWORDS)) {
					prev = Reflect.selectedSlot(mc);
					Reflect.selectSlot(mc, i);
					break;
				}
			}
		}
		CriticalsModule.cancelCrit = true;
		if (CriticalsModule.instance != null) CriticalsModule.instance.doCrit();
		mc.gameMode.attack(p, target);
		CriticalsModule.cancelCrit = false;
		p.swing(InteractionHand.MAIN_HAND);
		hitTicks = hitTicks();
		if (prev != -1) Reflect.selectSlot(mc, prev);
		if (sprint && returnSprint.value && dropSprint.value) p.setSprinting(true);
	}

	private int hitTicks() {
		if (oldDelay.value) return 1 + (int) (20f / rnd(minCps.getFloat(), Math.max(minCps.getFloat(), maxCps.getFloat())));
		return attackTickLimit.getInt();
	}

	private boolean autoCrit(Minecraft mc) {
		var p = mc.player;
		boolean skip = !smartCrit.value || p.getAbilities().flying || p.isFallFlying()
				|| dev.nebula.client.util.MoveUtil.amp("BLINDNESS") >= 0 || dev.nebula.client.util.MoveUtil.amp("SLOW_FALLING") >= 0;
		if (hitTicks > 0) return false;
		if (pauseInInventory.value && Compat.screen(mc) != null) return false;
		if (p.getAttackStrengthScale(attackBaseTime.getFloat()) < attackCooldown.getFloat() && !oldDelay.value) return false;
		if (!mc.options.keyJump.isDown() && !onlyCrit.value && !autoJump.value) return true;
		if (p.isInLava() || p.isUnderWater()) return true;
		if (p.fallDistance > 1 && p.fallDistance < 1.14) return false;
		if (!skip) return !p.onGround() && p.fallDistance > critFall.getFloat();
		return true;
	}

	private float range() {
		return attackRange.getFloat();
	}

	private boolean skip(Minecraft mc, Entity e) {
		if (e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive() || le.getHealth() <= 0) return true;
		if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) return true;
		if (ignoreInvisible.value && e.isInvisible()) return true;
		if (ignoreNamed.value && e.hasCustomName()) return true;
		if (e instanceof Player pl) {
			if (!players.value) return true;
			if (ignoreCreative.value && pl.isCreative()) return true;
		} else if (e instanceof Animal) {
			if (!animals.value) return true;
		} else if (e instanceof Villager) {
			if (!villagers.value) return true;
		} else if (e instanceof Enemy) {
			if (!hostiles.value) return true;
		} else if (!mobs.value) {
			return true;
		}
		double d = distSqFromEyes(mc, e.position().add(0, e.getEyeHeight(), 0));
		double r = range() + aimRange.get();
		if (rotationMode.index() == 1) r = range();
		if (d > r * r + 4) return true;
		return fovAngle(mc, e) > fov.get();
	}

	private static double distSqFromEyes(Minecraft mc, Vec3 v) {
		return mc.player.getEyePosition().distanceToSqr(v);
	}

	private double fovAngle(Minecraft mc, Entity e) {
		double dx = e.getX() - mc.player.getX();
		double dz = e.getZ() - mc.player.getZ();
		double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90;
		double diff = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - mc.player.getYRot()));
		return diff;
	}

	private void updateTarget(Minecraft mc) {
		Entity cand = findTarget(mc);
		if (target == null) {
			target = cand;
			return;
		}
		if (sort.index() == 2 || !lockTarget.value) target = cand;
		if (target != null && skip(mc, target)) target = null;
	}

	private Entity findTarget(Minecraft mc) {
		List<LivingEntity> list = new ArrayList<>();
		double r = range() + aimRange.get() + 4;
		for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(r))) {
			if (skip(mc, e) || !(e instanceof LivingEntity le)) continue;
			list.add(le);
		}
		Comparator<LivingEntity> byDist = Comparator.comparingDouble(e -> mc.player.distanceToSqr(e));
		return switch (sort.index()) {
			case 1 -> list.stream().max(byDist).orElse(null);
			case 2 -> list.stream().min(Comparator.comparingDouble(e -> fovAngle(mc, e))).orElse(null);
			case 3 -> list.stream().min(Comparator.comparingDouble(e -> e.getHealth() + e.getAbsorptionAmount())).orElse(null);
			case 4 -> list.stream().max(Comparator.comparingDouble(e -> e.getHealth() + e.getAbsorptionAmount())).orElse(null);
			default -> list.stream().min(byDist).orElse(null);
		};
	}

	/** Czy promien z oczu w kierunku (yaw, pitch) trafia hitbox celu w zasiegu (przez sciany do wallRange). */
	private boolean checkRtx(Minecraft mc, float yaw, float pitch, float range, float wall) {
		if (target == null) return false;
		double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
		Vec3 dir = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
		Vec3 eye = mc.player.getEyePosition();
		AABB box = target.getBoundingBox().inflate(target.getPickRadius());
		var hit = box.clip(eye, eye.add(dir.scale(range)));
		if (hit.isEmpty()) return false;
		double dist = eye.distanceTo(hit.get());
		if (dist <= wall) return true;
		return mc.player.hasLineOfSight(target);
	}

	private float[] calcAngle(Minecraft mc, Vec3 v) {
		Vec3 eye = mc.player.getEyePosition();
		double dx = v.x - eye.x, dy = v.y - eye.y, dz = v.z - eye.z;
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return new float[]{yaw, pitch};
	}

	/** "DVD-point": punkt wedruje po hitboxie celu; stad pochodzi celowanie (jak w ThunderHack). */
	private Vec3 legitLook(Minecraft mc) {
		float minXZ = 0.003f, maxXZ = 0.03f, minY = 0.001f, maxY = 0.03f;
		AABB b = target.getBoundingBox();
		double lx = b.getXsize(), ly = b.getYsize(), lz = b.getZsize();
		if (rotMotion.equals(Vec3.ZERO)) rotMotion = new Vec3(rnd(-0.05f, 0.05f), rnd(-0.05f, 0.05f), rnd(-0.05f, 0.05f));
		rotPoint = rotPoint.add(rotMotion);
		if (rotPoint.x >= (lx - 0.05) / 2f) rotMotion = new Vec3(-rnd(minXZ, maxXZ), rotMotion.y, rotMotion.z);
		if (rotPoint.y >= ly) rotMotion = new Vec3(rotMotion.x, -rnd(minY, maxY), rotMotion.z);
		if (rotPoint.z >= (lz - 0.05) / 2f) rotMotion = new Vec3(rotMotion.x, rotMotion.y, -rnd(minXZ, maxXZ));
		if (rotPoint.x <= -(lx - 0.05) / 2f) rotMotion = new Vec3(rnd(minXZ, 0.03f), rotMotion.y, rotMotion.z);
		if (rotPoint.y <= 0.05) rotMotion = new Vec3(rotMotion.x, rnd(minY, maxY), rotMotion.z);
		if (rotPoint.z <= -(lz - 0.05) / 2f) rotMotion = new Vec3(rotMotion.x, rotMotion.y, rnd(minXZ, maxXZ));

		if (!checkRtx(mc, rotYaw, rotPitch, range(), wallRange.getFloat())) {
			Vec3 center = target.position().add(0, target.getEyeHeight() / 2f, 0);
			float[] rot = calcAngle(mc, center);
			if (distSqFromEyes(mc, center) <= range() * range() && checkRtx(mc, rot[0], rot[1], range(), 0)) {
				rotPoint = new Vec3(rnd(-0.1f, 0.1f), target.getEyeHeight() / rnd(1.8f, 2.5f), rnd(-0.1f, 0.1f));
			} else {
				float half = (float) (lx / 2f);
				outer:
				for (float x1 = -half; x1 <= half; x1 += 0.05f) {
					for (float z1 = -half; z1 <= half; z1 += 0.05f) {
						for (float y1 = 0.05f; y1 <= ly; y1 += 0.15f) {
							Vec3 v1 = new Vec3(target.getX() + x1, target.getY() + y1, target.getZ() + z1);
							if (distSqFromEyes(mc, v1) > range() * range()) continue;
							float[] r2 = calcAngle(mc, v1);
							if (checkRtx(mc, r2[0], r2[1], range(), 0)) {
								rotPoint = new Vec3(x1, y1, z1);
								break outer;
							}
						}
					}
				}
			}
		}
		return target.position().add(rotPoint);
	}

	private void calcRotations(Minecraft mc) {
		var p = mc.player;
		if (target == null) return;
		Vec3 tv = p.isFallFlying() ? target.getEyePosition() : legitLook(mc);
		if (tv == null) return;
		boolean track = rotationMode.index() == 0;

		pitchAcc = checkRtx(mc, rotYaw, rotPitch, range() + aimRange.getFloat(), range() + aimRange.getFloat())
				? aimedPitchStep.getFloat()
				: pitchAcc < maxPitchStep.getFloat() ? pitchAcc * pitchAccelerate.getFloat() : maxPitchStep.getFloat();

		float dYaw = net.minecraft.util.Mth.wrapDegrees((float) net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(Math.atan2(tv.z - p.getZ(), tv.x - p.getX())) - 90) - rotYaw);
		float dPitch = (float) (-Math.toDegrees(Math.atan2(tv.y - p.getEyeY(), Math.sqrt(Math.pow(tv.x - p.getX(), 2) + Math.pow(tv.z - p.getZ(), 2)))) - rotPitch);

		float yawStep = track ? rnd(minYaw.getFloat(), maxYaw.getFloat()) : 360f;
		float pitchStep = track ? (pitchAcc + rnd(-1f, 1f)) : 180f;
		if (dYaw > 180) dYaw -= 180;
		float deltaYaw = Math.max(-yawStep, Math.min(yawStep, Math.abs(dYaw)));
		float deltaPitch = Math.max(-pitchStep, Math.min(pitchStep, dPitch));
		float newYaw = rotYaw + (dYaw > 0 ? deltaYaw : -deltaYaw);
		float newPitch = Math.max(-90f, Math.min(90f, rotPitch + deltaPitch));
		double gcd = Math.pow(mc.options.sensitivity().get() * 0.6 + 0.2, 3.0) * 1.2;
		if (track) {
			rotYaw = (float) (newYaw - (newYaw - rotYaw) % gcd);
			rotPitch = (float) (newPitch - (newPitch - rotPitch) % gcd);
			p.setYRot(rotYaw);
			p.setXRot(rotPitch);
		} else {
			rotYaw = p.getYRot();
			rotPitch = p.getXRot();
		}
		lookingAtHitbox = checkRtx(mc, rotYaw, rotPitch, range(), wallRange.getFloat());
	}
}

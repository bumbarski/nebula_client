package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Speed — logika przeniesiona z ThunderHack Recode (GPL-3.0, pan4ur): tryby NCP, StrictStrafe, MatrixJB. */
public final class SpeedModule extends Module {
	private final ModeSetting mode = add(new ModeSetting("Tryb", new String[]{"NCP", "StrictStrafe", "MatrixJB"}, 0));
	private final BoolSetting pauseInLiquids = add(new BoolSetting("Pauza w wodzie", false));
	private final BoolSetting pauseWhileSneaking = add(new BoolSetting("Pauza przy skradaniu", false));

	private double baseSpeed = 0.2873;
	private int stage = 1;
	private int ticks;
	private float prevForward;

	public SpeedModule() {
		super("speed", "Speed", "Szybkie bieganie (tryby z ThunderHack)", Category.MOVEMENT, false);
	}

	@Override
	protected void onEnable() {
		stage = 1;
		ticks = 0;
		baseSpeed = 0.2873;
	}

	@Override
	public void onTick(Minecraft mc) {
		var p = mc.player;
		if (p == null || mc.level == null || Compat.screen(mc) != null) return;
		if ((p.isInWater() || p.isInLava()) && pauseInLiquids.value) return;
		if (p.isShiftKeyDown() && pauseWhileSneaking.value) return;

		if (mode.index() == 2) {
			boolean close = !mc.level.noCollision(p, p.getBoundingBox().inflate(0.5, 0.0, 0.5).move(0.0, -1.0, 0.0));
			if (MoveUtil.isMoving() && close && p.fallDistance <= 0) {
				p.setOnGround(true);
				Vec3 d = p.getDeltaMovement();
				p.setDeltaMovement(d.x, MoveUtil.jumpSpeed() + 0.02, d.z);
			}
			return;
		}

		if (p.getAbilities().flying || p.isFallFlying() || p.getFoodData().getFoodLevel() <= 6) return;

		if (!MoveUtil.isMoving()) {
			Vec3 d = p.getDeltaMovement();
			p.setDeltaMovement(0, d.y, 0);
			return;
		}

		boolean strict = mode.index() == 1;
		float fwd = MoveUtil.forward();
		float currentSpeed = !strict && fwd <= 0 && prevForward > 0 ? MoveUtil.currentSpeed() * 0.66f : MoveUtil.currentSpeed();

		if (stage == 1 && p.onGround() && !p.horizontalCollision) {
			Vec3 d = p.getDeltaMovement();
			p.setDeltaMovement(d.x, MoveUtil.jumpSpeed(), d.z);
			baseSpeed *= 2.149;
			stage = 2;
		} else if (stage == 2) {
			baseSpeed = currentSpeed - (0.66 * (currentSpeed - MoveUtil.baseMoveSpeed()));
			stage = 3;
		} else {
			if (!mc.level.noCollision(p, p.getBoundingBox().move(0.0, p.getDeltaMovement().y, 0.0)) || p.verticalCollision) stage = 1;
			baseSpeed = currentSpeed - currentSpeed / 159.0D;
		}

		baseSpeed = Math.max(baseSpeed, MoveUtil.baseMoveSpeed());

		double ncpSpeed = strict || fwd < 1 ? 0.465 : 0.576;
		double ncpBypassSpeed = strict || fwd < 1 ? 0.44 : 0.57;

		if (MoveUtil.speedAmp() >= 0) {
			double amp = MoveUtil.speedAmp();
			ncpSpeed *= 1 + (0.2 * (amp + 1));
			ncpBypassSpeed *= 1 + (0.2 * (amp + 1));
		}
		if (MoveUtil.slowAmp() >= 0) {
			double amp = MoveUtil.slowAmp();
			ncpSpeed /= 1 + (0.2 * (amp + 1));
			ncpBypassSpeed /= 1 + (0.2 * (amp + 1));
		}

		baseSpeed = Math.min(baseSpeed, ticks > 25 ? ncpSpeed : ncpBypassSpeed);
		if (ticks++ > 50) ticks = 0;

		double[] dir = MoveUtil.forward(baseSpeed);
		Vec3 d = p.getDeltaMovement();
		p.setDeltaMovement(dir[0], d.y, dir[1]);
		prevForward = fwd;
	}
}

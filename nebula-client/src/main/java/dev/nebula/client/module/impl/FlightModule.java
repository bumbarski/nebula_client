package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Flight — tryby przeniesione z ThunderHack Recode (GPL-3.0, pan4ur): Vanilla, AirJump, MatrixGlide, MatrixJump, Creative. */
public final class FlightModule extends Module {
	private final ModeSetting mode = add(new ModeSetting("Tryb", new String[]{"Vanilla", "AirJump", "MatrixGlide", "MatrixJump", "Creative"}, 0));
	private final NumberSetting hSpeed = add(new NumberSetting("Poziomo", 0.0, 10.0, 1.0, 0.1));
	private final NumberSetting vSpeed = add(new NumberSetting("Pionowo", 0.0, 5.0, 0.78, 0.01));
	private final BoolSetting antiKick = add(new BoolSetting("AntiKick (Vanilla/Creative)", false));
	private int flyTicks;

	public FlightModule() {
		super("flight", "Flight", "Latanie (tryby z ThunderHack)", Category.MOVEMENT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		var p = mc.player;
		if (p == null || mc.level == null || Compat.screen(mc) != null) return;
		boolean up = mc.options.keyJump.isDown();
		boolean down = mc.options.keyShift.isDown();
		switch (mode.index()) {
			case 0 -> {
				if (MoveUtil.isMoving()) {
					double[] d = MoveUtil.forward(hSpeed.get());
					p.setDeltaMovement(d[0], 0, d[1]);
				} else {
					p.setDeltaMovement(0, 0, 0);
				}
				if (up) p.setDeltaMovement(p.getDeltaMovement().add(0, vSpeed.get(), 0));
				if (down) p.setDeltaMovement(p.getDeltaMovement().add(0, -vSpeed.get(), 0));
			}
			case 1 -> {
				if (MoveUtil.isMoving() && !mc.level.noCollision(p, p.getBoundingBox().inflate(0.5, 0.0, 0.5).move(0.0, -1.0, 0.0))) {
					p.setOnGround(true);
					Vec3 d = p.getDeltaMovement();
					p.setDeltaMovement(d.x, MoveUtil.jumpSpeed() + 0.02, d.z);
				}
			}
			case 2 -> {
				if (p.onGround()) {
					Vec3 d = p.getDeltaMovement();
					p.setDeltaMovement(d.x, MoveUtil.jumpSpeed() + 0.02, d.z);
					flyTicks = 5;
				} else if (flyTicks > 0) {
					if (MoveUtil.isMoving()) {
						double[] d = MoveUtil.forward(hSpeed.get());
						p.setDeltaMovement(d[0], -0.04, d[1]);
					} else {
						p.setDeltaMovement(0, -0.04, 0);
					}
					flyTicks--;
				}
			}
			case 3 -> {
				if (p.fallDistance == 0) return;
				p.getAbilities().flying = false;
				p.setDeltaMovement(0, 0, 0);
				if (up) p.setDeltaMovement(p.getDeltaMovement().add(0, vSpeed.get(), 0));
				if (down) p.setDeltaMovement(p.getDeltaMovement().add(0, -vSpeed.get(), 0));
				double[] d = MoveUtil.forward(hSpeed.get());
				p.setDeltaMovement(d[0], p.getDeltaMovement().y, d[1]);
			}
			case 4 -> {
				p.getAbilities().flying = true;
				p.getAbilities().setFlyingSpeed(hSpeed.getFloat() / 10f);
			}
		}
		if (antiKick.value && (mode.index() == 0 || mode.index() == 4)) {
			p.setDeltaMovement(p.getDeltaMovement().add(0, -0.08, 0));
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.getAbilities().flying = false;
			mc.player.getAbilities().setFlyingSpeed(0.05f);
		}
	}
}

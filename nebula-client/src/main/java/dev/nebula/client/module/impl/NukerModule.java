package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Nuker — logika przeniesiona z ThunderHack Recode (GPL-3.0, pan4ur): tryb Select (typ bloku wybierasz klikajac go LPM) lub All,
 * zasieg, opoznienie w ms, Flatten, IgnoreWalls, AvoidLava; celuje w blok i kopie go (rotacja widoczna po stronie klienta).
 */
public final class NukerModule extends Module {
	private final ModeSetting blocks = add(new ModeSetting("Blocks", new String[]{"Select", "All"}, 0));
	private final NumberSetting range = add(new NumberSetting("Range", 1.5, 6.0, 4.2, 0.1));
	private final NumberSetting delay = add(new NumberSetting("Delay (ms)", 0, 1000, 25, 5));
	private final BoolSetting ignoreWalls = add(new BoolSetting("Ignore Walls", false));
	private final BoolSetting flatten = add(new BoolSetting("Flatten", false));
	private final BoolSetting avoidLava = add(new BoolSetting("Avoid Lava", false));
	private Block targetType;
	private BlockPos current;
	private long last;

	public NukerModule() {
		super("nuker", "Nuker", "Breaks blocks around you (logic from ThunderHack)", Category.GAME, false);
	}

	@Override
	protected void onDisable() {
		current = null;
		targetType = null;
	}

	@Override
	public void onTick(Minecraft mc) {
		var p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null || Compat.screen(mc) != null) return;
		// wybor typu bloku przez klikniecie
		if (blocks.index() == 0 && mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult hr) {
			BlockState s = mc.level.getBlockState(hr.getBlockPos());
			if (!s.isAir()) targetType = s.getBlock();
		}
		if (mc.options.keyAttack.isDown()) return;
		if (blocks.index() == 0 && targetType == null) return;

		double r = range.get();
		if (current != null) {
			BlockState cs = mc.level.getBlockState(current);
			if (cs.isAir() || (blocks.index() == 0 && cs.getBlock() != targetType)
					|| p.getEyePosition().distanceToSqr(Vec3.atCenterOf(current)) > r * r) current = null;
		}
		if (current == null) current = find(mc, r);
		if (current == null) return;

		Vec3 c = Vec3.atCenterOf(current);
		Vec3 eye = p.getEyePosition();
		double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
		p.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90));
		p.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));

		if (System.currentTimeMillis() - last < delay.get()) return;
		Direction face = Direction.getApproximateNearest(-dx, -dy, -dz);
		mc.gameMode.startDestroyBlock(current, face);
		mc.gameMode.continueDestroyBlock(current, face);
		p.swing(InteractionHand.MAIN_HAND);
		last = System.currentTimeMillis();
	}

	private BlockPos find(Minecraft mc, double r) {
		BlockPos base = mc.player.blockPosition();
		int ri = (int) Math.ceil(r);
		BlockPos best = null;
		double bd = Double.MAX_VALUE;
		Vec3 eye = mc.player.getEyePosition();
		for (int x = -ri; x <= ri; x++) {
			for (int y = -ri; y <= ri; y++) {
				for (int z = -ri; z <= ri; z++) {
					BlockPos pos = base.offset(x, y, z);
					BlockState st = mc.level.getBlockState(pos);
					if (st.isAir() || st.getDestroySpeed(mc.level, pos) < 0) continue;
					if (blocks.index() == 0 && st.getBlock() != targetType) continue;
					if (flatten.value && pos.getY() < mc.player.getBlockY()) continue;
					double d = eye.distanceToSqr(Vec3.atCenterOf(pos));
					if (d > r * r || d >= bd) continue;
					boolean exposed = false;
					boolean lava = false;
					for (Direction dir : Direction.values()) {
						BlockPos n = pos.relative(dir);
						if (mc.level.getBlockState(n).isAir()) exposed = true;
						if (mc.level.getFluidState(n).is(FluidTags.LAVA)) lava = true;
					}
					if (!ignoreWalls.value && !exposed) continue;
					if (avoidLava.value && lava) continue;
					bd = d;
					best = pos;
				}
			}
		}
		return best;
	}
}

package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Nuker: kopie po jednym bloku na tick wokol ciebie (zasieg regulowany). */
public final class NukerModule extends Module {
	private final NumberSetting range = add(new NumberSetting("Zasięg", 1, 5, 4, 1));

	public NukerModule() {
		super("nuker", "Nuker", "Automatycznie niszczy bloki wokół ciebie", Category.GAME, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.gameMode == null || Compat.screen(mc) != null) return;
		int r = range.getInt();
		BlockPos base = mc.player.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					BlockPos p = base.offset(x, y, z);
					BlockState st = mc.level.getBlockState(p);
					if (st.isAir() || st.getDestroySpeed(mc.level, p) < 0) continue;
					double d = x * x + y * y + z * z;
					if (d < bestD) {
						bestD = d;
						best = p;
					}
				}
			}
		}
		if (best == null) return;
		mc.gameMode.startDestroyBlock(best, Direction.UP);
		mc.gameMode.continueDestroyBlock(best, Direction.UP);
		mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
	}
}

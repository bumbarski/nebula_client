package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Podczas kopania wybiera z paska najszybsze narzedzie do trafionego bloku. */
public final class AutoToolModule extends Module {
	public AutoToolModule() {
		super("autotool", "Auto Tool", "Sam wybiera najlepsze narzędzie do bloku", Category.GAME, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.level == null || Compat.screen(mc) != null) return;
		if (!mc.options.keyAttack.isDown() || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
		BlockState st = mc.level.getBlockState(hit.getBlockPos());
		int best = Reflect.selectedSlot(mc);
		float bestS = mc.player.getInventory().getItem(best).getDestroySpeed(st);
		for (int i = 0; i < 9; i++) {
			float s = mc.player.getInventory().getItem(i).getDestroySpeed(st);
			if (s > bestS) {
				bestS = s;
				best = i;
			}
		}
		Reflect.selectSlot(mc, best);
	}
}

package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Auto Trap / Auto Web: stawia bloki (obsydian albo pajeczyne) wokol najblizszego gracza. */
public final class AutoTrapModule extends Module {
	private final ModeSetting mode = add(new ModeSetting("Tryb", new String[]{"Pułapka z bloków", "Pajęczyna"}, 0));
	private final NumberSetting range = add(new NumberSetting("Zasięg", 2, 5, 4.5, 0.5));
	private int ticks;

	private static final int[][] CAGE = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}, {0, 2, 0}};

	public AutoTrapModule() {
		super("autotrap", "Auto Trap / Web", "Stawia bloki lub pajęczynę wokół najbliższego gracza", Category.COMBAT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.gameMode == null || Compat.screen(mc) != null) return;
		if (++ticks < 2) return;
		ticks = 0;
		Player target = null;
		double bd = range.get();
		for (Player p : mc.level.players()) {
			if (p == mc.player) continue;
			double d = mc.player.distanceTo(p);
			if (d < bd) {
				bd = d;
				target = p;
			}
		}
		if (target == null) return;
		BlockPos base = target.blockPosition();
		boolean web = mode.index() == 1;
		int slot = findSlot(mc, web);
		if (slot < 0) return;
		java.util.List<BlockPos> wanted = new java.util.ArrayList<>();
		if (web) {
			wanted.add(base);
			wanted.add(base.above());
		} else {
			for (int[] o : CAGE) wanted.add(base.offset(o[0], o[1], o[2]));
		}
		for (BlockPos p : wanted) {
			if (!mc.level.getBlockState(p).isAir()) continue;
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (mc.level.getBlockState(n).isAir()) continue;
				if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(n)) > 5.5) continue;
				Direction face = d.getOpposite();
				Vec3 hitPos = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
				int old = Reflect.selectedSlot(mc);
				Reflect.selectSlot(mc, slot);
				mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hitPos, face, n, false));
				mc.player.swing(InteractionHand.MAIN_HAND);
				Reflect.selectSlot(mc, old);
				return;
			}
		}
	}

	private static int findSlot(Minecraft mc, boolean web) {
		int fallback = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack st = mc.player.getInventory().getItem(i);
			if (st.isEmpty()) continue;
			if (web) {
				if (st.is(Items.COBWEB)) return i;
			} else {
				if (st.is(Items.OBSIDIAN)) return i;
				if (fallback < 0 && st.getItem() instanceof BlockItem && !st.is(Items.COBWEB)) fallback = i;
			}
		}
		return fallback;
	}
}

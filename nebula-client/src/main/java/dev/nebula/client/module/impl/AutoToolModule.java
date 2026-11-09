package dev.nebula.client.module.impl;

import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** AutoTool — logika przeniesiona z ThunderHack Recode (GPL-3.0, pan4ur): najszybsze narzedzie, SwapBack po 300 ms, SaveItem. */
public final class AutoToolModule extends Module {
	private final BoolSetting swapBack = add(new BoolSetting("Wróć do poprzedniego", true));
	private final BoolSetting saveItem = add(new BoolSetting("Oszczędzaj przedmioty (<10 wytrz.)", true));
	private int lastSlot = -1;
	private boolean swap;
	private long swapDelay;

	public AutoToolModule() {
		super("autotool", "Auto Tool", "Sam wybiera najlepsze narzędzie do bloku", Category.GAME, false);
	}

	private static int tool(Minecraft mc, BlockState st, boolean save) {
		int index = -1;
		float fastest = 1.0f;
		for (int i = 0; i < 9; i++) {
			ItemStack s = mc.player.getInventory().getItem(i);
			if (s.isEmpty()) continue;
			if (save && !(s.getMaxDamage() - s.getDamageValue() > 10) && s.getMaxDamage() > 0) continue;
			float speed = s.getDestroySpeed(st);
			if (speed > fastest) {
				fastest = speed;
				index = i;
			}
		}
		return index;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.level == null) return;
		if (!(mc.hitResult instanceof BlockHitResult r)) return;
		BlockState st = mc.level.getBlockState(r.getBlockPos());
		if (st.isAir()) return;
		int t = tool(mc, st, saveItem.value);
		if (t != -1 && mc.options.keyAttack.isDown()) {
			if (lastSlot == -1) lastSlot = Reflect.selectedSlot(mc);
			Reflect.selectSlot(mc, t);
			swap = true;
			swapDelay = System.currentTimeMillis();
		} else if (swap && lastSlot != -1 && System.currentTimeMillis() >= swapDelay + 300 && swapBack.value) {
			Reflect.selectSlot(mc, lastSlot);
			lastSlot = -1;
			swap = false;
		}
	}
}

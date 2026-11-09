package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/** AutoTool — logika przeniesiona z ThunderHack Recode (GPL-3.0, pan4ur): najszybsze narzedzie z paska, powrot po 300 ms. */
public final class AutoToolModule extends Module {
	private final BoolSetting swapBack = add(new BoolSetting("Wróć do poprzedniego", true));
	private final BoolSetting saveItem = add(new BoolSetting("Oszczędzaj niszczące się", true));
	private int lastSlot = -1;
	private long swapTime;

	public AutoToolModule() {
		super("autotool", "AutoTool", "Wybiera najlepsze narzędzie do bloku, który kopiesz", Category.GAME, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.level == null || Compat.screen(mc) != null) return;
		if (!(mc.hitResult instanceof BlockHitResult res)) return;
		BlockPos pos = res.getBlockPos();
		if (mc.level.getBlockState(pos).isAir()) return;
		int tool = getTool(mc, pos);
		if (tool != -1 && mc.options.keyAttack.isDown()) {
			if (lastSlot == -1) lastSlot = Reflect.selectedSlot(mc);
			Reflect.selectSlot(mc, tool);
			swapTime = System.currentTimeMillis();
		} else if (lastSlot != -1 && System.currentTimeMillis() >= swapTime + 300 && swapBack.value) {
			Reflect.selectSlot(mc, lastSlot);
			lastSlot = -1;
		}
	}

	private int getTool(Minecraft mc, BlockPos pos) {
		int index = -1;
		float fastest = 1.0f;
		var state = mc.level.getBlockState(pos);
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getItem(i);
			if (stack.isEmpty()) continue;
			if (saveItem.value && stack.getMaxDamage() > 0 && !(stack.getMaxDamage() - stack.getDamageValue() > 10)) continue;
			float speed = stack.getDestroySpeed(state);
			if (speed > fastest) {
				fastest = speed;
				index = i;
			}
		}
		return index;
	}
}

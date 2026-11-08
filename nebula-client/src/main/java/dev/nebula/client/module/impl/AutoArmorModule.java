package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/** Zaklada zbroje z ekwipunku na puste miejsca na zbroje. */
public final class AutoArmorModule extends Module {
	private int ticks;

	public AutoArmorModule() {
		super("autoarmor", "Auto Armor", "Sam zakłada zbroję z ekwipunku", Category.GAME, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null) return;
		if (++ticks < 4) return;
		ticks = 0;
		var menu = mc.player.inventoryMenu;
		for (int i = 9; i < 45; i++) {
			ItemStack st = menu.getSlot(i).getItem();
			if (st.isEmpty()) continue;
			Equippable eq = st.get(DataComponents.EQUIPPABLE);
			if (eq == null) continue;
			int target = switch (eq.slot()) {
				case HEAD -> 5;
				case CHEST -> 6;
				case LEGS -> 7;
				case FEET -> 8;
				default -> -1;
			};
			if (target < 0 || !menu.getSlot(target).getItem().isEmpty()) continue;
			dev.nebula.client.Compat.quickMove(mc, menu.containerId, i);
			return;
		}
	}
}

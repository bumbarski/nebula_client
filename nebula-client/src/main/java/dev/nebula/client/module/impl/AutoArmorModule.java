package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * AutoArmor — schemat przeniesiony z ThunderHack Recode (GPL-3.0, pan4ur): dla kazdej czesci zbroi wybiera najlepszy element
 * z ekwipunku (i wymienia, jesli jest lepszy od zalozonego), z opoznieniem w tickach i opcja NoMove. Ocena: material, zaklecia, wytrzymalosc.
 */
public final class AutoArmorModule extends Module {
	private final NumberSetting delay = add(new NumberSetting("Opóźnienie (ticki)", 0, 10, 5, 1));
	private final BoolSetting pauseInventory = add(new BoolSetting("Pauza w ekwipunku", false));
	private final BoolSetting noMove = add(new BoolSetting("Tylko w bezruchu", false));
	private int tickDelay;

	public AutoArmorModule() {
		super("autoarmor", "Auto Armor", "Sam zakłada i wymienia na lepszą zbroję", Category.GAME, false);
	}

	private static int rank(ItemStack st) {
		String id = st.getItem().getDescriptionId();
		if (id.contains("netherite")) return 6;
		if (id.contains("diamond")) return 5;
		if (id.contains("iron")) return 4;
		if (id.contains("chainmail")) return 3;
		if (id.contains("golden") || id.contains("gold")) return 2;
		if (id.contains("turtle")) return 2;
		return 1;
	}

	private static int score(ItemStack st) {
		if (st.isEmpty()) return -1;
		Equippable eq = st.get(DataComponents.EQUIPPABLE);
		if (eq == null) return -1;
		int s = rank(st) * 100 + (st.isEnchanted() ? 30 : 0);
		if (st.getMaxDamage() > 0) s += (int) (10f * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
		return s;
	}

	private static int armorSlotIndex(EquipmentSlot s) {
		return switch (s) {
			case HEAD -> 5;
			case CHEST -> 6;
			case LEGS -> 7;
			case FEET -> 8;
			default -> -1;
		};
	}

	@Override
	public void onTick(Minecraft mc) {
		var p = mc.player;
		if (p == null || mc.gameMode == null) return;
		if (Compat.screen(mc) != null && pauseInventory.value) return;
		if (tickDelay-- > 0) return;
		var menu = p.inventoryMenu;
		for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
			int armorSlot = armorSlotIndex(es);
			int current = score(menu.getSlot(armorSlot).getItem());
			int bestSlot = -1;
			int bestScore = current;
			for (int i = 9; i < 45; i++) {
				ItemStack st = menu.getSlot(i).getItem();
				Equippable eq = st.get(DataComponents.EQUIPPABLE);
				if (eq == null || eq.slot() != es) continue;
				int sc = score(st);
				if (sc > bestScore) {
					bestScore = sc;
					bestSlot = i;
				}
			}
			if (bestSlot == -1) continue;
			if (MoveUtil.isMoving() && noMove.value) return;
			Reflect.click(mc, menu.containerId, bestSlot, 0, "PICKUP");
			Reflect.click(mc, menu.containerId, armorSlot, 0, "PICKUP");
			if (current != -1) Reflect.click(mc, menu.containerId, bestSlot, 0, "PICKUP");
			tickDelay = delay.getInt();
			return;
		}
	}
}

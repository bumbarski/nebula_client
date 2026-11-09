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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * AutoArmor — schemat przeniesiony z ThunderHack Recode (GPL-3.0, pan4ur): dla kazdego z 4 miejsc na zbroje szuka w ekwipunku
 * najlepszej czesci i podmienia (PICKUP-PICKUP-PICKUP), z opoznieniem w tickach. Ocena: material (netherite > diamond > ...) + zaklecia.
 */
public final class AutoArmorModule extends Module {
	private final NumberSetting delay = add(new NumberSetting("Opóźnienie (ticki)", 0, 10, 5, 1));
	private final BoolSetting noMove = add(new BoolSetting("Nie w ruchu", false));
	private final BoolSetting pauseInventory = add(new BoolSetting("Pauza z otwartym oknem", false));
	private int tickDelay;

	public AutoArmorModule() {
		super("autoarmor", "AutoArmor", "Zakłada najlepszą zbroję z ekwipunku", Category.GAME, false);
	}

	private static int score(ItemStack st) {
		if (st.isEmpty()) return -1;
		Equippable eq = st.get(DataComponents.EQUIPPABLE);
		if (eq == null) return 0;
		String id = st.getItem().toString();
		int base = id.contains("netherite") ? 60 : id.contains("diamond") ? 50 : id.contains("iron") ? 40 : id.contains("chainmail") ? 30 : id.contains("golden") || id.contains("gold") ? 25 : id.contains("turtle") ? 20 : id.contains("leather") ? 10 : 5;
		if (st.isEnchanted()) base += 8;
		if (st.getMaxDamage() > 0) base += (int) (5f * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
		return base;
	}

	private static int armorSlotIndex(ItemStack st) {
		Equippable eq = st.get(DataComponents.EQUIPPABLE);
		if (eq == null) return -1;
		return switch (eq.slot()) {
			case HEAD -> 5;
			case CHEST -> 6;
			case LEGS -> 7;
			case FEET -> 8;
			default -> -1;
		};
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null) return;
		if (Compat.screen(mc) != null && pauseInventory.value) return;
		if (tickDelay-- > 0) return;
		if (noMove.value && MoveUtil.isMoving()) return;
		var menu = mc.player.inventoryMenu;
		for (int armor = 5; armor <= 8; armor++) {
			int cur = score(menu.getSlot(armor).getItem());
			int best = -1;
			int bestScore = cur;
			for (int i = 9; i < 45; i++) {
				ItemStack st = menu.getSlot(i).getItem();
				if (armorSlotIndex(st) != armor) continue;
				int s = score(st);
				if (s > bestScore) {
					bestScore = s;
					best = i;
				}
			}
			if (best == -1) continue;
			Reflect.click(mc, menu.containerId, best, 0, "PICKUP");
			Reflect.click(mc, menu.containerId, armor, 0, "PICKUP");
			if (cur != -1) Reflect.click(mc, menu.containerId, best, 0, "PICKUP");
			tickDelay = delay.getInt();
			return;
		}
	}
}

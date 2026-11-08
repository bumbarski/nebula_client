package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

/** Trzyma totem nieśmiertelności w drugiej ręce. */
public final class OffHandModule extends Module {
	private int ticks;

	public OffHandModule() {
		super("offhand", "Off Hand", "Sam przekłada totem do drugiej ręki", Category.COMBAT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null || Compat.screen(mc) != null) return;
		if (++ticks < 3) return;
		ticks = 0;
		if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
		var menu = mc.player.inventoryMenu;
		for (int i = 9; i < 45; i++) {
			if (menu.getSlot(i).getItem().is(Items.TOTEM_OF_UNDYING)) {
				Reflect.click(mc, menu.containerId, i, 40, "SWAP");
				return;
			}
		}
	}
}

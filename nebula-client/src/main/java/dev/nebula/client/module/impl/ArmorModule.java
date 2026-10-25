package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ArmorModule extends HudModule {
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
	};
	private static final String[] LABELS = {"Hełm", "Napierśnik", "Spodnie", "Buty", "Ręka"};

	private final BoolSetting hand = add(new BoolSetting("Pokaż przedmiot w ręce", false));

	public ArmorModule() {
		// x bardzo duze = przyklejone do prawej krawedzi (HudManager przycina do ekranu)
		super("armor", "Pancerz", "Wytrzymałość założonego pancerza", Category.HUD, false, 10_000, 4);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		List<String[]> rows = new ArrayList<>();
		int count = hand.value ? SLOTS.length : SLOTS.length - 1;
		for (int i = 0; i < count; i++) {
			ItemStack stack = mc.player == null ? ItemStack.EMPTY : mc.player.getItemBySlot(SLOTS[i]);
			if (stack.isEmpty()) {
				if (preview) rows.add(new String[]{LABELS[i], "-"});
				continue;
			}
			String value;
			if (stack.isDamageableItem()) {
				int max = stack.getMaxDamage();
				value = (max - stack.getDamageValue()) + "/" + max;
			} else {
				value = "x" + stack.getCount();
			}
			rows.add(new String[]{LABELS[i], value});
		}

		if (rows.isEmpty()) {
			hide();
			return;
		}
		drawRows(g, rows);
	}
}

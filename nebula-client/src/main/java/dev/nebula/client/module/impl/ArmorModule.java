package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.ui.Gfx;
import dev.nebula.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class ArmorModule extends HudModule {
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
	};

	private final BoolSetting hand = add(new BoolSetting("Pokaż przedmiot w ręce", false));
	private final BoolSetting percent = add(new BoolSetting("Wytrzymałość w procentach", false));

	public ArmorModule() {
		// x bardzo duze = przyklejone do prawej krawedzi (HudManager przycina do ekranu)
		super("armor", "Pancerz", "Ikony założonego pancerza z wytrzymałością", Category.HUD, false, 10_000, 4);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		final int pad = 3;
		final int rowH = 18;
		int count = hand.value ? SLOTS.length : SLOTS.length - 1;

		ItemStack[] stacks = new ItemStack[count];
		String[] texts = new String[count];
		int shown = 0;
		int textW = 0;
		for (int i = 0; i < count; i++) {
			ItemStack s = mc.player == null ? ItemStack.EMPTY : mc.player.getItemBySlot(SLOTS[i]);
			stacks[i] = s;
			if (s.isEmpty()) {
				texts[i] = preview ? "-" : null;
			} else if (s.isDamageableItem()) {
				int max = s.getMaxDamage();
				int left = max - s.getDamageValue();
				texts[i] = percent.value ? Math.round(left * 100f / max) + "%" : String.valueOf(left);
			} else {
				texts[i] = s.getCount() > 1 ? "x" + s.getCount() : "";
			}
			if (texts[i] != null) {
				shown++;
				textW = Math.max(textW, Gfx.width(texts[i]));
			}
		}
		if (shown == 0) {
			hide();
			return;
		}

		w = pad * 2 + 3 + 16 + 4 + textW;
		h = shown * rowH + pad * 2 - 2;
		if (background.value) {
			Gfx.roundRect(g, x, y, w, h, 3, Gfx.alpha(Theme.BG, Theme.opacity()));
		}
		Gfx.roundRect(g, x + 1, y + 3, 2, h - 6, 1, Theme.accent());

		var font = mc.font;
		int ty = y + pad;
		for (int i = 0; i < count; i++) {
			if (texts[i] == null) continue;
			int ix = x + pad + 3;
			if (!stacks[i].isEmpty()) {
				g.item(stacks[i], ix, ty);
			}
			int color = 0xFFFFFFFF;
			if (stacks[i].isDamageableItem()) {
				float frac = 1f - (float) stacks[i].getDamageValue() / stacks[i].getMaxDamage();
				color = frac > 0.5f ? 0xFFFFFFFF : frac > 0.2f ? 0xFFFFD54A : 0xFFFF5555;
			}
			g.text(font, texts[i], ix + 20, ty + 4, color, shadow.value);
			ty += rowH;
		}
	}
}

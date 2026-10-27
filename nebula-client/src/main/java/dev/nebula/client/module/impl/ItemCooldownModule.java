package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.ui.Gfx;
import dev.nebula.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pokazuje przedmioty z paska i drugiej reki, ktore sa na cooldownie (perla, jablko, tarcza itd.). */
public final class ItemCooldownModule extends HudModule {
	public ItemCooldownModule() {
		super("itemcooldown", "Cooldown przedmiotów", "Ikony przedmiotów z aktywnym cooldownem", Category.HUD, false, 4, 80);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		List<ItemStack> stacks = new ArrayList<>();
		List<Float> left = new ArrayList<>();
		if (mc.player != null) {
			var cooldowns = mc.player.getCooldowns();
			Set<Object> seen = new HashSet<>();
			List<ItemStack> candidates = new ArrayList<>();
			for (int i = 0; i < 9; i++) candidates.add(mc.player.getInventory().getItem(i));
			candidates.add(mc.player.getItemBySlot(EquipmentSlot.OFFHAND));
			for (ItemStack s : candidates) {
				if (s.isEmpty() || !cooldowns.isOnCooldown(s)) continue;
				if (!seen.add(cooldowns.getCooldownGroup(s))) continue;
				stacks.add(s);
				left.add(cooldowns.getCooldownPercent(s, 0f));
			}
		}
		if (stacks.isEmpty() && preview) {
			stacks.add(new ItemStack(Items.ENDER_PEARL));
			left.add(0.6f);
		}
		if (stacks.isEmpty()) {
			hide();
			return;
		}

		final int pad = 3;
		final int rowH = 18;
		final int barW = 36;
		w = pad * 2 + 3 + 16 + 4 + barW;
		h = stacks.size() * rowH + pad * 2 - 2;
		if (background.value) {
			Gfx.roundRect(g, x, y, w, h, 3, Gfx.alpha(Theme.BG, Theme.opacity()));
		}
		Gfx.roundRect(g, x + 1, y + 3, 2, h - 6, 1, Theme.accent());

		int ty = y + pad;
		for (int i = 0; i < stacks.size(); i++) {
			int ix = x + pad + 3;
			g.item(stacks.get(i), ix, ty);
			int bx = ix + 20;
			int by = ty + 6;
			Gfx.roundRect(g, bx, by, barW, 4, 2, 0x55FFFFFF);
			int fill = Math.max(1, Math.round(barW * (1f - Math.max(0f, Math.min(1f, left.get(i))))));
			Gfx.roundRect(g, bx, by, fill, 4, 2, Theme.accent());
			ty += rowH;
		}
	}
}

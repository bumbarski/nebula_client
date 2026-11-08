package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/** Przy ataku automatycznie wybiera z paska miecz albo topor. */
public final class AttributeSwapModule extends Module {
	private final ModeSetting prefer = add(new ModeSetting("Preferuj", new String[]{"Miecz", "Topór"}, 0));

	public AttributeSwapModule() {
		super("attributeswap", "Attribute Swap", "Przy ataku wybiera miecz lub topór z paska", Category.COMBAT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || Compat.screen(mc) != null) return;
		if (!mc.options.keyAttack.isDown() || mc.crosshairPickEntity == null) return;
		boolean sword = prefer.index() == 0;
		int fallback = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack st = mc.player.getInventory().getItem(i);
			boolean isSword = st.is(ItemTags.SWORDS);
			boolean isAxe = st.is(ItemTags.AXES);
			if ((sword && isSword) || (!sword && isAxe)) {
				Reflect.selectSlot(mc, i);
				return;
			}
			if (fallback < 0 && (isSword || isAxe)) fallback = i;
		}
		if (fallback >= 0) Reflect.selectSlot(mc, fallback);
	}
}

package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.*;
import net.minecraft.client.Minecraft;

/** Krecisz sie w miejscu (widoczne dla innych graczy). */
public final class AntiAimModule extends Module {
	private final NumberSetting speed = add(new NumberSetting("Rotation Speed", 5, 90, 30, 5));

	public AntiAimModule() {
		super("antiaim", "Anti Aim", "Spins your character", Category.COMBAT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || Compat.screen(mc) != null) return;
		mc.player.setYRot(mc.player.getYRot() + speed.getFloat());
	}
}

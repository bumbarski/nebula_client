package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.*;
import net.minecraft.client.Minecraft;

/** Brak przerwy miedzy kopaniem kolejnych blokow. */
public final class SpeedMineModule extends Module {
	public SpeedMineModule() {
		super("speedmine", "Speed Mine", "No delay between breaking blocks", Category.GAME, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.gameMode != null) Reflect.setIntField(mc.gameMode, "destroyDelay", 0);
	}
}

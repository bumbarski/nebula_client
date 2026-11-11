package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.*;
import net.minecraft.client.Minecraft;

/** Brak opoznienia skoku przy trzymaniu spacji. */
public final class NoDelayModule extends Module {
	public NoDelayModule() {
		super("nodelay", "No Delay", "No jump delay", Category.MOVEMENT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player != null) Reflect.setIntField(mc.player, "noJumpDelay", 0);
	}
}

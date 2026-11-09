package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;

/** NoPush — gracze i moby nie odpychaja cie (przez mixin na Entity.push, jak w ThunderHack Recode, GPL-3.0, pan4ur). */
public final class NoPushModule extends Module {
	public static boolean active;

	public NoPushModule() {
		super("nopush", "NoPush", "Brak odpychania przez graczy i moby", Category.MOVEMENT, false);
	}

	@Override
	protected void onEnable() {
		active = true;
	}

	@Override
	protected void onDisable() {
		active = false;
	}
}

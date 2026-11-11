package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;

/** No Push: blokuje wypychanie gracza z blokow (np. gdy perla wyladuje w scianie). Gracze i moby dalej cie popychaja. */
public final class NoPushModule extends Module {
	public static boolean active;

	public NoPushModule() {
		super("nopush", "No Push", "Blocks do not push you out of themselves (e.g. after a pearl)", Category.MOVEMENT, false);
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

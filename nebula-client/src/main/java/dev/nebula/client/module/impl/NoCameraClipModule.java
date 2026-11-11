package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;

/** Kamera 3. osoby przechodzi przez sciany zamiast sie przy nich przyblizac. */
public final class NoCameraClipModule extends Module {
	public static boolean active;

	public NoCameraClipModule() {
		super("nocameraclip", "No Camera Clip", "Third-person camera does not clip into walls", Category.VISUAL, false);
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

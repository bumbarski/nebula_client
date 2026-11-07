package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.NumberSetting;

/** Jasnosc gry powyzej limitu suwaka z opcji (bez efektu widzenia w nocy). */
public final class GammaModule extends Module {
	public static GammaModule instance;

	private final NumberSetting gamma = add(new NumberSetting("Gamma", 0, 15, 10, 0.5));

	public GammaModule() {
		super("gamma", "Gamma", "Rozjaśnia świat powyżej maksimum z opcji gry", Category.VISUAL, false);
		instance = this;
	}

	public double value() {
		return gamma.get();
	}
}

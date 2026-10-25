package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.ui.Theme;

/** Ustawienia wygladu calego klienta (motyw, przezroczystosc, animacje). Zawsze aktywny. */
public final class AppearanceModule extends Module {
	public final ModeSetting theme = add(new ModeSetting("Motyw", Theme.NAMES, 0));
	public final NumberSetting opacity = add(new NumberSetting("Przezroczystość tła", 0.30, 1.0, 0.85, 0.05));
	public final BoolSetting animations = add(new BoolSetting("Animacje", true));

	public AppearanceModule() {
		super("appearance", "Wygląd", "Motyw kolorystyczny, przezroczystość i animacje", Category.VISUAL, true);
		this.toggleable = false;
		Theme.bind(this);
	}
}

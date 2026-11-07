package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.NumberSetting;

/** Pozycja, rozmiar i obrot reki oraz przedmiotu w pierwszej osobie. */
public final class ViewModelModule extends Module {
	public static ViewModelModule instance;

	public final NumberSetting x = add(new NumberSetting("Przesunięcie X", -1, 1, 0, 0.05));
	public final NumberSetting y = add(new NumberSetting("Przesunięcie Y", -1, 1, 0, 0.05));
	public final NumberSetting z = add(new NumberSetting("Przesunięcie Z", -1, 1, 0, 0.05));
	public final NumberSetting scale = add(new NumberSetting("Rozmiar", 0.3, 2, 1, 0.05));
	public final NumberSetting rotX = add(new NumberSetting("Obrót X", -90, 90, 0, 1));
	public final NumberSetting rotY = add(new NumberSetting("Obrót Y", -90, 90, 0, 1));
	public final NumberSetting rotZ = add(new NumberSetting("Obrót Z", -90, 90, 0, 1));

	public ViewModelModule() {
		super("viewmodel", "View model", "Ustaw pozycję i rozmiar ręki oraz przedmiotu", Category.VISUAL, false);
		instance = this;
	}
}

package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;

/** Sprint i skradanie w trybie przelaczania (jedno klikniecie) albo przytrzymania. */
public final class ToggleKeysModule extends Module {
	private static final String[] MODES = {"Przytrzymaj", "Przełącz"};

	private final ModeSetting sprint = add(new ModeSetting("Sprint", MODES, 1));
	private final ModeSetting sneak = add(new ModeSetting("Skradanie", MODES, 0));

	private boolean captured;
	private boolean origSprint;
	private boolean origSneak;

	public ToggleKeysModule() {
		super("togglekeys", "Sprint i skradanie", "Wybierz: przytrzymanie albo przełączanie klawisza", Category.GAME, true);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (!captured) {
			origSprint = mc.options.toggleSprint().get();
			origSneak = mc.options.toggleCrouch().get();
			captured = true;
		}
		mc.options.toggleSprint().set(sprint.index() == 1);
		mc.options.toggleCrouch().set(sneak.index() == 1);
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (captured && mc != null && mc.options != null) {
			mc.options.toggleSprint().set(origSprint);
			mc.options.toggleCrouch().set(origSneak);
		}
		captured = false;
	}
}

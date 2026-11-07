package dev.nebula.client.module;

import dev.nebula.client.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
	public final String id;
	public final String name;
	public final String description;
	public final Category category;
	public final List<Setting> settings = new ArrayList<>();

	private boolean enabled;
	protected boolean toggleable = true;

	protected Module(String id, String name, String description, Category category, boolean defaultEnabled) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabled = defaultEnabled;
	}

	protected <S extends Setting> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isToggleable() {
		return toggleable;
	}

	public void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		if (value) onEnable(); else onDisable();
	}

	public void toggle() {
		if (toggleable) setEnabled(!enabled);
	}

	/** Wywolywane co tick (20x/s), tylko gdy modul wlaczony i gracz jest w swiecie. */
	public void onTick(Minecraft mc) {
	}

	/** Wywolywane co klatke przy rysowaniu HUD, tylko gdy modul wlaczony. */
	public void onFrame(Minecraft mc) {
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}
}

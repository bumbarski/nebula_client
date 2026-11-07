package dev.nebula.client.setting;

import com.google.gson.JsonObject;

public final class BoolSetting extends Setting {
	public boolean value;

	public BoolSetting(String name, boolean defaultValue) {
		super(name);
		this.value = defaultValue;
	}

	public void toggle() {
		value = !value;
	}

	@Override
	public void save(JsonObject out) {
		out.addProperty(name, value);
	}

	@Override
	public void load(JsonObject in) {
		if (!in.has(name)) return;
		try {
			value = in.get(name).getAsBoolean();
		} catch (RuntimeException ignored) {
		}
	}
}

package dev.nebula.client.setting;

import com.google.gson.JsonObject;

public abstract class Setting {
	public final String name;

	protected Setting(String name) {
		this.name = name;
	}

	public abstract void save(JsonObject out);

	public abstract void load(JsonObject in);
}

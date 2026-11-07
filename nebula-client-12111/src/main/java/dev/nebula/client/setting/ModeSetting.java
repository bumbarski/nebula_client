package dev.nebula.client.setting;

import com.google.gson.JsonObject;

public final class ModeSetting extends Setting {
	public final String[] modes;
	private int index;

	public ModeSetting(String name, String[] modes, int defaultIndex) {
		super(name);
		this.modes = modes;
		this.index = Math.max(0, Math.min(modes.length - 1, defaultIndex));
	}

	public String get() {
		return modes[index];
	}

	public int index() {
		return index;
	}

	public void cycle(int direction) {
		index = Math.floorMod(index + direction, modes.length);
	}

	@Override
	public void save(JsonObject out) {
		out.addProperty(name, get());
	}

	@Override
	public void load(JsonObject in) {
		if (!in.has(name)) return;
		try {
			String wanted = in.get(name).getAsString();
			for (int i = 0; i < modes.length; i++) {
				if (modes[i].equals(wanted)) {
					index = i;
					return;
				}
			}
		} catch (RuntimeException ignored) {
		}
	}
}

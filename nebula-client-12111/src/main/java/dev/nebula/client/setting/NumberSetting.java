package dev.nebula.client.setting;

import com.google.gson.JsonObject;

import java.util.Locale;

public final class NumberSetting extends Setting {
	public final double min;
	public final double max;
	public final double step;
	private double value;

	public NumberSetting(String name, double min, double max, double defaultValue, double step) {
		super(name);
		this.min = min;
		this.max = max;
		this.step = step;
		set(defaultValue);
	}

	public double get() {
		return value;
	}

	public float getFloat() {
		return (float) value;
	}

	public int getInt() {
		return (int) Math.round(value);
	}

	/** Przyciaga do kroku i ogranicza do zakresu. */
	public void set(double v) {
		if (step > 0) {
			v = Math.round((v - min) / step) * step + min;
		}
		v = Math.max(min, Math.min(max, v));
		this.value = Math.round(v * 1000.0) / 1000.0;
	}

	public double fraction() {
		return max == min ? 0 : (value - min) / (max - min);
	}

	public String format() {
		return step >= 1 ? String.valueOf(getInt()) : String.format(Locale.ROOT, "%.2f", value);
	}

	@Override
	public void save(JsonObject out) {
		out.addProperty(name, value);
	}

	@Override
	public void load(JsonObject in) {
		if (!in.has(name)) return;
		try {
			set(in.get(name).getAsDouble());
		} catch (RuntimeException ignored) {
		}
	}
}

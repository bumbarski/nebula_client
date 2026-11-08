package dev.nebula.client.setting;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Ustawienie z wlasnym rysowaniem w menu (np. siatka wyboru z podgladem). Nie zapisuje sie w konfiguracji Nebuli. */
public abstract class CustomSetting extends Setting {
	protected CustomSetting(String name) {
		super(name);
	}

	/** Wysokosc w pikselach dla podanej szerokosci obszaru. */
	public abstract int height(int width);

	public abstract void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float a);

	public abstract boolean click(int x, int y, int w, double mx, double my, int button);

	@Override
	public void save(JsonObject out) {
	}

	@Override
	public void load(JsonObject in) {
	}
}

package dev.nebula.client.module;

import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.ui.Gfx;
import dev.nebula.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Modul rysowany na ekranie, ktory mozna przesuwac w edytorze HUD. */
public abstract class HudModule extends Module {
	public int x;
	public int y;
	/** Rozmiar z ostatniej klatki, uzywany do przesuwania i przyciagania. */
	public int w;
	public int h;

	protected final BoolSetting background = add(new BoolSetting("Tło", true));
	protected final BoolSetting shadow = add(new BoolSetting("Cień tekstu", false));

	protected HudModule(String id, String name, String description, Category category,
						boolean defaultEnabled, int x, int y) {
		super(id, name, description, category, defaultEnabled);
		this.x = x;
		this.y = y;
	}

	/**
	 * Rysuje element w (x, y) i musi ustawic w oraz h.
	 * preview = true w edytorze HUD (pokaz przykladowe dane nawet bez efektow itp.).
	 */
	public abstract void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview);

	/** Wspolny styl: ciemne zaokraglone tlo, akcent z lewej, etykieta w kolorze motywu. */
	protected void drawRows(GuiGraphicsExtractor g, List<String[]> rows) {
		final int pad = 4;
		final int lineH = 10;
		final int bar = 3;

		int maxW = 0;
		for (String[] r : rows) {
			int rw = r[0].isEmpty() ? Gfx.width(r[1]) : Gfx.width(r[0]) + 4 + Gfx.width(r[1]);
			maxW = Math.max(maxW, rw);
		}
		w = maxW + pad * 2 + bar;
		h = rows.size() * lineH + pad * 2 - 2;

		if (background.value) {
			Gfx.roundRect(g, x, y, w, h, 3, Gfx.alpha(Theme.BG, Theme.opacity()));
		}
		Gfx.roundRect(g, x + 1, y + 3, 2, h - 6, 1, Theme.accent());

		int ty = y + pad;
		for (String[] r : rows) {
			int tx = x + pad + bar;
			if (!r[0].isEmpty()) {
				Gfx.text(g, r[0], tx, ty, Theme.accent(), shadow.value);
				tx += Gfx.width(r[0]) + 4;
			}
			Gfx.text(g, r[1], tx, ty, 0xFFFFFFFF, shadow.value);
			ty += lineH;
		}
	}

	protected void hide() {
		w = 0;
		h = 0;
	}
}

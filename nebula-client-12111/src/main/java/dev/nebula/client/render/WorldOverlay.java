package dev.nebula.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Modul rysujacy elementy swiata (ESP, tracery) jako nakladke 2D rzutowana z pozycji kamery, wiec widoczna przez bloki. */
public interface WorldOverlay {
	void drawWorld(GuiGraphics g, Minecraft mc, Projector p);
}

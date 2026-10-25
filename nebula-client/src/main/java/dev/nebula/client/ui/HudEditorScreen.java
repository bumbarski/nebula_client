package dev.nebula.client.ui;

import dev.nebula.client.NebulaClient;
import dev.nebula.client.config.ConfigManager;
import dev.nebula.client.hud.HudManager;
import dev.nebula.client.module.HudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Edytor pozycji elementow HUD: przeciagaj myszka, krawedzie i srodek ekranu przyciagaja. */
public final class HudEditorScreen extends Screen {
	private static final int SNAP = 5;
	private static final int MARGIN = 2;

	private final Screen parent;
	private HudModule dragging;
	private int dragOffX;
	private int dragOffY;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Edytor HUD"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Gfx.rect(g, 0, 0, width, height, Gfx.alpha(0xFF05050A, 0.45f));

		// linie pomocnicze przy przeciaganiu
		if (dragging != null) {
			int guide = Gfx.alpha(Theme.accent(), 0.35f);
			Gfx.rect(g, width / 2, 0, 1, height, guide);
			Gfx.rect(g, 0, height / 2, width, 1, guide);
		}

		List<HudModule> all = NebulaClient.modules().hud();
		for (HudModule m : all) {
			if (!m.isEnabled()) continue;
			HudManager.clamp(m, width, height);
			m.draw(g, minecraft, true);
		}

		for (HudModule m : all) {
			if (!m.isEnabled() || m.w <= 0) continue;
			boolean hovered = isInside(m, mouseX, mouseY);
			boolean active = m == dragging;
			int color = active ? Theme.accent() : hovered ? Gfx.alpha(Theme.accent(), 0.8f) : Gfx.alpha(0xFFFFFFFF, 0.25f);
			Gfx.outline(g, m.x - 1, m.y - 1, m.w + 2, m.h + 2, color);
			if (hovered || active) {
				Gfx.text(g, m.name, m.x, m.y - 11, Theme.TEXT, true);
			}
		}

		String hint = "Przeciągnij elementy myszką   |   ESC - wróć do menu";
		Gfx.text(g, hint, (width - Gfx.width(hint)) / 2, height - 20, Theme.MUTED, false);
	}

	private static boolean isInside(HudModule m, double mx, double my) {
		return mx >= m.x && mx < m.x + m.w && my >= m.y && my < m.y + m.h;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			List<HudModule> all = NebulaClient.modules().hud();
			// od gory (ostatnio narysowany) do dolu
			for (int i = all.size() - 1; i >= 0; i--) {
				HudModule m = all.get(i);
				if (m.isEnabled() && m.w > 0 && isInside(m, event.x(), event.y())) {
					dragging = m;
					dragOffX = (int) event.x() - m.x;
					dragOffY = (int) event.y() - m.y;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging == null) return super.mouseDragged(event, dragX, dragY);

		int nx = (int) event.x() - dragOffX;
		int ny = (int) event.y() - dragOffY;

		nx = snap(nx, dragging.w, width);
		ny = snap(ny, dragging.h, height);

		dragging.x = Math.max(0, Math.min(Math.max(0, width - dragging.w), nx));
		dragging.y = Math.max(0, Math.min(Math.max(0, height - dragging.h), ny));
		return true;
	}

	/** Przyciaga pozycje do marginesu, drugiej krawedzi i srodka ekranu. */
	private static int snap(int pos, int size, int screen) {
		if (Math.abs(pos - MARGIN) < SNAP) return MARGIN;
		if (Math.abs(pos + size - (screen - MARGIN)) < SNAP) return screen - MARGIN - size;
		if (Math.abs(pos + size / 2 - screen / 2) < SNAP) return screen / 2 - size / 2;
		return pos;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean was = dragging != null;
		dragging = null;
		if (was) ConfigManager.save(NebulaClient.modules());
		return was || super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		ConfigManager.save(NebulaClient.modules());
		minecraft.setScreenAndShow(parent);
	}
}

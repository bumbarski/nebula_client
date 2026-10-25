package dev.nebula.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nebula.client.NebulaClient;
import dev.nebula.client.config.ConfigManager;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Glowne menu klienta (Prawy Shift). Panele per kategoria, przelaczniki modulow,
 * rozwijane ustawienia (PPM), suwaki, przeciaganie paneli.
 */
public final class ClickGuiScreen extends Screen {
	private static final int PANEL_W = 132;
	private static final int HEADER_H = 22;
	private static final int ROW_H = 18;

	// pozycje paneli i rozwiniecia przezywaja zamkniecie menu
	private static final Map<Category, int[]> POSITIONS = new EnumMap<>(Category.class);
	private static final Set<Module> EXPANDED = new HashSet<>();

	private final Map<Module, Float> toggleAnim = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();

	private final long openedAt = System.nanoTime();
	private long lastFrame = System.nanoTime();

	private Category draggingPanel;
	private int dragOffX;
	private int dragOffY;
	private Hit activeSlider;

	public ClickGuiScreen() {
		super(Component.literal("Nebula"));
	}

	private static int[] pos(Category c) {
		return POSITIONS.computeIfAbsent(c, k -> new int[]{24 + k.ordinal() * (PANEL_W + 16), 28});
	}

	/** Obszar klikalny zapamietany podczas rysowania. */
	private static final class Hit {
		final int x, y, w, h;
		Runnable onLeft;
		Runnable onRight;
		Category dragPanel;
		NumberSetting slider;
		int sliderX, sliderW;

		Hit(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- rysowanie

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;

		float open = Theme.animations() ? Math.min(1f, (now - openedAt) / 180_000_000f) : 1f;
		float ease = 1f - (1f - open) * (1f - open) * (1f - open);

		hits.clear();

		// przyciemnione tlo z lekkim akcentem u dolu
		Gfx.gradient(g, 0, 0, width, height,
				Gfx.alpha(0xFF05050A, 0.55f * ease),
				Gfx.alpha(Gfx.lerp(0xFF05050A, Theme.accentDark(), 0.35f), 0.70f * ease));

		for (Category cat : Category.values()) {
			drawPanel(g, cat, mouseX, mouseY, dt, ease);
		}

		drawFooter(g, mouseX, mouseY, ease);
	}

	private void drawPanel(GuiGraphicsExtractor g, Category cat, int mx, int my, float dt, float a) {
		List<Module> modules = NebulaClient.modules().in(cat);
		if (modules.isEmpty()) return;

		int[] p = pos(cat);
		int px = p[0];
		int py = p[1] + Math.round((1f - a) * -14f);
		int totalH = panelHeight(modules);

		// cien, korpus, naglowek
		Gfx.roundRect(g, px - 1, py - 1, PANEL_W + 2, totalH + 3, 6, Gfx.alpha(0xFF000000, 0.35f * a));
		Gfx.roundRect(g, px, py, PANEL_W, totalH, 5, Gfx.alpha(Theme.PANEL, Theme.opacity() * a));
		Gfx.roundRect(g, px, py, PANEL_W, HEADER_H, 5, Gfx.alpha(Theme.HEADER, a));
		Gfx.rect(g, px, py + HEADER_H - 5, PANEL_W, 5, Gfx.alpha(Theme.HEADER, a));
		Gfx.rect(g, px, py + HEADER_H - 1, PANEL_W, 1, Gfx.alpha(Theme.accent(), a));
		Gfx.roundRect(g, px + 8, py + 7, 3, 8, 1, Gfx.alpha(Theme.accent(), a));
		Gfx.text(g, cat.label, px + 16, py + (HEADER_H - Gfx.fontHeight()) / 2, Gfx.alpha(Theme.TEXT, a), false);

		Hit header = new Hit(px, py, PANEL_W, HEADER_H);
		header.dragPanel = cat;
		hits.add(header);

		int cy = py + HEADER_H + 3;
		for (Module m : modules) {
			cy = drawModule(g, m, px, cy, mx, my, dt, a);
		}
	}

	private int drawModule(GuiGraphicsExtractor g, Module m, int px, int cy, int mx, int my, float dt, float a) {
		boolean hover = mx >= px + 2 && mx < px + PANEL_W - 2 && my >= cy && my < cy + ROW_H;
		if (hover) {
			Gfx.roundRect(g, px + 3, cy, PANEL_W - 6, ROW_H, 3, Gfx.alpha(Theme.ROW_HOVER, a));
		}

		boolean on = m.isEnabled();
		int nameColor = (!m.isToggleable() || on) ? Theme.TEXT : Theme.MUTED;
		Gfx.text(g, m.name, px + 10, cy + (ROW_H - Gfx.fontHeight()) / 2, Gfx.alpha(nameColor, a), false);

		boolean hasSettings = !m.settings.isEmpty();
		boolean expanded = EXPANDED.contains(m);

		if (m.isToggleable()) {
			float t = toggleAnim.getOrDefault(m, on ? 1f : 0f);
			float goal = on ? 1f : 0f;
			t = Theme.animations() ? t + (goal - t) * Math.min(1f, dt * 14f) : goal;
			toggleAnim.put(m, t);

			int sx = px + PANEL_W - 30;
			int sy = cy + (ROW_H - 10) / 2;
			Gfx.roundRect(g, sx, sy, 20, 10, 5, Gfx.alpha(Gfx.lerp(Theme.TRACK, Theme.accent(), t), a));
			Gfx.roundRect(g, sx + 2 + Math.round(t * 10f), sy + 2, 6, 6, 3, Gfx.alpha(0xFFFFFFFF, a));

			if (hasSettings) {
				Gfx.text(g, expanded ? "-" : "+", px + PANEL_W - 40, cy + (ROW_H - Gfx.fontHeight()) / 2,
						Gfx.alpha(Theme.MUTED, a), false);
			}
		} else if (hasSettings) {
			Gfx.text(g, expanded ? "-" : "+", px + PANEL_W - 16, cy + (ROW_H - Gfx.fontHeight()) / 2,
					Gfx.alpha(Theme.MUTED, a), false);
		}

		Hit row = new Hit(px + 2, cy, PANEL_W - 4, ROW_H);
		Runnable expand = () -> {
			if (m.settings.isEmpty()) return;
			if (!EXPANDED.add(m)) EXPANDED.remove(m);
		};
		row.onLeft = m.isToggleable() ? m::toggle : expand;
		row.onRight = expand;
		hits.add(row);
		cy += ROW_H;

		if (expanded && hasSettings) {
			cy += drawSettings(g, m, px, cy, a);
		}
		return cy;
	}

	private int drawSettings(GuiGraphicsExtractor g, Module m, int px, int cy, float a) {
		int blockH = settingsHeight(m);
		Gfx.roundRect(g, px + 4, cy, PANEL_W - 8, blockH, 3, Gfx.alpha(0xFF0B0B12, 0.65f * a));

		int lx = px + 10;
		int rx = px + PANEL_W - 10;
		int y = cy + 1;
		for (Setting s : m.settings) {
			int sh = settingHeight(s);
			Hit hit = new Hit(px + 4, y, PANEL_W - 8, sh);

			if (s instanceof BoolSetting b) {
				Gfx.text(g, b.name, lx, y + 4, Gfx.alpha(Theme.TEXT, a), false);
				Gfx.roundRect(g, rx - 10, y + 3, 10, 10, 3, Gfx.alpha(b.value ? Theme.accent() : Theme.TRACK, a));
				hit.onLeft = b::toggle;
			} else if (s instanceof ModeSetting ms) {
				Gfx.text(g, ms.name, lx, y + 4, Gfx.alpha(Theme.TEXT, a), false);
				String v = ms.get();
				Gfx.text(g, v, rx - Gfx.width(v), y + 4, Gfx.alpha(Theme.accent(), a), false);
				hit.onLeft = () -> ms.cycle(1);
				hit.onRight = () -> ms.cycle(-1);
			} else if (s instanceof NumberSetting ns) {
				Gfx.text(g, ns.name, lx, y + 3, Gfx.alpha(Theme.TEXT, a), false);
				String v = ns.format();
				Gfx.text(g, v, rx - Gfx.width(v), y + 3, Gfx.alpha(Theme.accent(), a), false);

				int bx = lx;
				int bw = rx - lx;
				int by = y + 16;
				int fill = Math.round(bw * (float) ns.fraction());
				Gfx.roundRect(g, bx, by, bw, 3, 1, Gfx.alpha(Theme.TRACK, a));
				Gfx.roundRect(g, bx, by, Math.max(2, fill), 3, 1, Gfx.alpha(Theme.accent(), a));
				Gfx.roundRect(g, bx + fill - 3, by - 2, 6, 7, 3, Gfx.alpha(0xFFFFFFFF, a));

				hit.slider = ns;
				hit.sliderX = bx;
				hit.sliderW = bw;
			}
			hits.add(hit);
			y += sh;
		}
		return blockH;
	}

	private void drawFooter(GuiGraphicsExtractor g, int mx, int my, float a) {
		String hint = "PPM - ustawienia modułu   |   Prawy Shift / ESC - zamknij";
		Gfx.text(g, hint, (width - Gfx.width(hint)) / 2, height - 44, Gfx.alpha(Theme.MUTED, a), false);

		String label = "Edytor HUD";
		int bw = Gfx.width(label) + 24;
		int bh = 18;
		int bx = (width - bw) / 2;
		int by = height - 28;
		boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + bh;

		Gfx.roundRect(g, bx, by, bw, bh, 4,
				Gfx.alpha(hover ? Theme.accent() : Theme.accentDark(), 0.9f * a));
		Gfx.text(g, label, bx + 12, by + (bh - Gfx.fontHeight()) / 2 + 1, Gfx.alpha(Theme.TEXT, a), false);

		Hit hit = new Hit(bx, by, bw, bh);
		hit.onLeft = () -> {
			ConfigManager.save(NebulaClient.modules());
			minecraft.setScreenAndShow(new HudEditorScreen(this));
		};
		hits.add(hit);
	}

	// ---------------------------------------------------------------- uklad

	private int panelHeight(List<Module> modules) {
		int h = HEADER_H + 3;
		for (Module m : modules) {
			h += ROW_H;
			if (EXPANDED.contains(m) && !m.settings.isEmpty()) h += settingsHeight(m);
		}
		return h + 3;
	}

	private static int settingsHeight(Module m) {
		int h = 2;
		for (Setting s : m.settings) h += settingHeight(s);
		return h;
	}

	private static int settingHeight(Setting s) {
		return s instanceof NumberSetting ? 26 : 16;
	}

	// ---------------------------------------------------------------- wejscie

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();

		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (!hit.contains(mx, my)) continue;

			if (hit.dragPanel != null && button == 0) {
				int[] p = pos(hit.dragPanel);
				draggingPanel = hit.dragPanel;
				dragOffX = (int) mx - p[0];
				dragOffY = (int) my - p[1];
				return true;
			}
			if (hit.slider != null && button == 0) {
				activeSlider = hit;
				updateSlider(mx);
				return true;
			}
			if (button == 0 && hit.onLeft != null) {
				hit.onLeft.run();
			} else if (button == 1 && hit.onRight != null) {
				hit.onRight.run();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingPanel != null) {
			int[] p = pos(draggingPanel);
			p[0] = Math.max(0, Math.min(width - PANEL_W, (int) event.x() - dragOffX));
			p[1] = Math.max(0, Math.min(height - HEADER_H, (int) event.y() - dragOffY));
			return true;
		}
		if (activeSlider != null) {
			updateSlider(event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean changed = draggingPanel != null || activeSlider != null;
		draggingPanel = null;
		activeSlider = null;
		if (changed) ConfigManager.save(NebulaClient.modules());
		return changed || super.mouseReleased(event);
	}

	private void updateSlider(double mouseX) {
		if (activeSlider == null || activeSlider.sliderW <= 0) return;
		NumberSetting s = activeSlider.slider;
		double t = Math.max(0.0, Math.min(1.0, (mouseX - activeSlider.sliderX) / activeSlider.sliderW));
		s.set(s.min + t * (s.max - s.min));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RSHIFT) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		ConfigManager.save(NebulaClient.modules());
		super.onClose();
	}
}

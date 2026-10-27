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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Glowne menu klienta (Prawy Shift): jedno okno, kategorie po lewej, karty modulow z plynnymi
 * przelacznikami, rozwijane ustawienia (PPM lub strzalka), wyszukiwarka i przewijanie.
 */
public final class ClickGuiScreen extends Screen {
	private static final int SIDEBAR = 120;
	private static final int CARD_H = 34;
	private static final int GAP = 6;

	// wybrana zakladka i rozwiniecia przezywaja zamkniecie menu
	private static int tab = 0;
	private static final Set<Module> EXPANDED = new HashSet<>();

	private static final int CARD = 0xFF1E1E2B;
	private static final int CARD_HOVER = 0xFF262637;
	private static final int SETTINGS_BG = 0xFF161621;

	private final Map<Module, Float> toggleAnim = new HashMap<>();
	private final Map<Module, Float> expandAnim = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();

	private final long openedAt = System.nanoTime();
	private long lastFrame = System.nanoTime();

	private String query = "";
	private float scroll;
	private float scrollTarget;
	private int viewTop;
	private int viewBottom;
	private int contentH;
	private Hit activeSlider;

	public ClickGuiScreen() {
		super(Component.literal("Nebula"));
	}

	/** Obszar klikalny zapamietany podczas rysowania. */
	private static final class Hit {
		final int x, y, w, h;
		final boolean inList;
		Runnable onLeft;
		Runnable onRight;
		NumberSetting slider;
		int sliderX, sliderW;

		Hit(int x, int y, int w, int h, boolean inList) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.inList = inList;
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

		float open = Theme.animations() ? Math.min(1f, (now - openedAt) / 200_000_000f) : 1f;
		float a = 1f - (1f - open) * (1f - open) * (1f - open);

		hits.clear();

		Gfx.gradient(g, 0, 0, width, height,
				Gfx.alpha(0xFF05050A, 0.50f * a),
				Gfx.alpha(Gfx.lerp(0xFF05050A, Theme.accentDark(), 0.30f), 0.66f * a));

		int winW = Math.min(width - 24, 500);
		int winH = Math.min(height - 24, 320);
		int wx = (width - winW) / 2;
		int wy = (height - winH) / 2 + Math.round((1f - a) * 12f);

		// miekki cien, okno, pasek boczny
		for (int i = 6; i >= 1; i--) {
			Gfx.roundRect(g, wx - i, wy - i, winW + i * 2, winH + i * 2, 8 + i, Gfx.alpha(0xFF000000, 0.07f * a));
		}
		Gfx.roundRect(g, wx, wy, winW, winH, 8, Gfx.alpha(Theme.PANEL, Math.max(0.94f, Theme.opacity()) * a));
		Gfx.roundRect(g, wx, wy, SIDEBAR + 8, winH, 8, Gfx.alpha(Theme.HEADER, a));
		Gfx.rect(g, wx + SIDEBAR, wy, 8, winH, Gfx.alpha(Theme.PANEL, Math.max(0.94f, Theme.opacity()) * a));
		Gfx.rect(g, wx + SIDEBAR, wy + 8, 1, winH - 16, Gfx.alpha(0xFFFFFFFF, 0.05f * a));

		drawSidebar(g, wx, wy, winH, mouseX, mouseY, a);
		drawContent(g, wx + SIDEBAR + 14, wy, winW - SIDEBAR - 28, winH, mouseX, mouseY, dt, a);
	}

	private void drawSidebar(GuiGraphicsExtractor g, int wx, int wy, int winH, int mx, int my, float a) {
		// logo
		Gfx.roundRect(g, wx + 14, wy + 15, 3, 20, 1, Gfx.alpha(Theme.accent(), a));
		Gfx.text(g, "NEBULA", wx + 23, wy + 15, Gfx.alpha(Theme.TEXT, a), false);
		Gfx.text(g, "client", wx + 23, wy + 26, Gfx.alpha(Theme.accent(), a), false);

		Category[] cats = Category.values();
		int ty = wy + 52;
		for (int i = 0; i < cats.length; i++) {
			Category c = cats[i];
			int count = NebulaClient.modules().in(c).size();
			boolean selected = query.isEmpty() && i == tab;
			boolean hover = mx >= wx + 8 && mx < wx + SIDEBAR - 8 && my >= ty && my < ty + 24;

			if (selected) {
				Gfx.roundRect(g, wx + 8, ty, SIDEBAR - 16, 24, 6, Gfx.alpha(Theme.accent(), 0.20f * a));
				Gfx.roundRect(g, wx + 8, ty + 6, 2, 12, 1, Gfx.alpha(Theme.accent(), a));
			} else if (hover) {
				Gfx.roundRect(g, wx + 8, ty, SIDEBAR - 16, 24, 6, Gfx.alpha(Theme.ROW_HOVER, a));
			}
			Gfx.text(g, c.label, wx + 18, ty + 8, Gfx.alpha(selected ? Theme.TEXT : Theme.MUTED, a), false);
			String n = String.valueOf(count);
			Gfx.text(g, n, wx + SIDEBAR - 14 - Gfx.width(n), ty + 8, Gfx.alpha(Theme.MUTED, 0.7f * a), false);

			Hit hit = new Hit(wx + 8, ty, SIDEBAR - 16, 24, false);
			final int index = i;
			hit.onLeft = () -> {
				tab = index;
				query = "";
				scroll = 0;
				scrollTarget = 0;
			};
			hits.add(hit);
			ty += 27;
		}

		// przycisk edytora HUD
		int bx = wx + 10;
		int bw = SIDEBAR - 20;
		int by = wy + winH - 34;
		boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + 24;
		Gfx.roundRect(g, bx, by, bw, 24, 6, Gfx.alpha(hover ? Theme.accent() : Theme.accentDark(), 0.95f * a));
		String label = "Edytor HUD";
		Gfx.text(g, label, bx + (bw - Gfx.width(label)) / 2, by + 8, Gfx.alpha(Theme.TEXT, a), false);
		Hit hit = new Hit(bx, by, bw, 24, false);
		hit.onLeft = () -> {
			ConfigManager.save(NebulaClient.modules());
			minecraft.gui.setScreen(new HudEditorScreen(this));
		};
		hits.add(hit);
	}

	private void drawContent(GuiGraphicsExtractor g, int cx, int wy, int cw, int winH,
							 int mx, int my, float dt, float a) {
		// wyszukiwarka
		Gfx.roundRect(g, cx, wy + 10, cw, 22, 6, Gfx.alpha(0xFF12121B, a));
		boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
		if (query.isEmpty()) {
			Gfx.text(g, "Szukaj modułów...  (PPM = ustawienia)", cx + 10, wy + 17, Gfx.alpha(Theme.MUTED, 0.8f * a), false);
			String cat = Category.values()[tab].label;
			Gfx.text(g, cat, cx + cw - 10 - Gfx.width(cat), wy + 17, Gfx.alpha(Theme.accent(), a), false);
		} else {
			Gfx.text(g, query + (caret ? "_" : ""), cx + 10, wy + 17, Gfx.alpha(Theme.TEXT, a), false);
		}

		viewTop = wy + 40;
		viewBottom = wy + winH - 10;
		int viewH = viewBottom - viewTop;

		List<Module> modules = visibleModules();
		int[] heights = new int[modules.size()];
		contentH = 0;
		for (int i = 0; i < modules.size(); i++) {
			Module m = modules.get(i);
			float goal = EXPANDED.contains(m) && !m.settings.isEmpty() ? 1f : 0f;
			float f = expandAnim.getOrDefault(m, goal);
			f = Theme.animations() ? f + (goal - f) * Math.min(1f, dt * 14f) : goal;
			if (Math.abs(goal - f) < 0.01f) f = goal;
			expandAnim.put(m, f);
			float eased = f * f * (3f - 2f * f);
			heights[i] = CARD_H + Math.round((settingsHeight(m) + 4) * eased);
			contentH += heights[i] + GAP;
		}

		scrollTarget = Math.max(0f, Math.min(scrollTarget, Math.max(0, contentH - viewH)));
		scroll = Theme.animations() ? scroll + (scrollTarget - scroll) * Math.min(1f, dt * 16f) : scrollTarget;

		if (modules.isEmpty()) {
			String none = "Brak wyników";
			Gfx.text(g, none, cx + (cw - Gfx.width(none)) / 2, viewTop + 30, Gfx.alpha(Theme.MUTED, a), false);
			return;
		}

		int cardW = cw - 8;
		g.enableScissor(cx - 2, viewTop, cx + cw + 4, viewBottom);
		int y = viewTop - Math.round(scroll);
		for (int i = 0; i < modules.size(); i++) {
			int h = heights[i];
			if (y + h >= viewTop && y <= viewBottom) {
				drawCard(g, modules.get(i), cx, y, cardW, h, mx, my, dt, a);
			}
			y += h + GAP;
		}
		g.disableScissor();

		// pasek przewijania
		if (contentH > viewH) {
			float frac = viewH / (float) contentH;
			int barH = Math.max(18, Math.round(viewH * frac));
			int barY = viewTop + Math.round((viewH - barH) * (scroll / Math.max(1f, contentH - viewH)));
			Gfx.roundRect(g, cx + cw - 3, barY, 3, barH, 1, Gfx.alpha(Theme.accent(), 0.7f * a));
		}
	}

	private void drawCard(GuiGraphicsExtractor g, Module m, int x, int y, int w, int total,
						  int mx, int my, float dt, float a) {
		boolean inView = my >= viewTop && my < viewBottom;
		boolean hover = inView && mx >= x && mx < x + w && my >= y && my < y + CARD_H;
		boolean on = m.isEnabled();
		boolean hasSettings = !m.settings.isEmpty();
		boolean expanded = EXPANDED.contains(m);

		Gfx.roundRect(g, x, y, w, total, 6, Gfx.alpha(hover ? CARD_HOVER : CARD, a));
		if (on && m.isToggleable()) {
			Gfx.roundRect(g, x, y + 7, 2, CARD_H - 14, 1, Gfx.alpha(Theme.accent(), a));
		}

		boolean lit = !m.isToggleable() || on;
		Gfx.text(g, m.name, x + 12, y + 7, Gfx.alpha(lit ? Theme.TEXT : Theme.MUTED, a), false);
		Gfx.text(g, fit(m.description, w - 12 - 74), x + 12, y + 19, Gfx.alpha(Theme.MUTED, 0.85f * a), false);

		if (m.isToggleable()) {
			float t = toggleAnim.getOrDefault(m, on ? 1f : 0f);
			float goal = on ? 1f : 0f;
			t = Theme.animations() ? t + (goal - t) * Math.min(1f, dt * 14f) : goal;
			toggleAnim.put(m, t);

			int sx = x + w - 44;
			int sy = y + 9;
			Gfx.roundRect(g, sx, sy, 32, 16, 8, Gfx.alpha(Gfx.lerp(Theme.TRACK, Theme.accent(), t), a));
			Gfx.roundRect(g, sx + 2 + Math.round(t * 16f), sy + 2, 12, 12, 6, Gfx.alpha(0xFFFFFFFF, a));
		}
		if (hasSettings) {
			String arrow = expanded ? "v" : ">";
			int ax = x + w - (m.isToggleable() ? 62 : 22);
			Gfx.text(g, arrow, ax, y + 13, Gfx.alpha(expanded ? Theme.accent() : Theme.MUTED, a), false);
		}

		Runnable expand = () -> {
			if (m.settings.isEmpty()) return;
			if (!EXPANDED.add(m)) EXPANDED.remove(m);
		};
		Hit row = new Hit(x, y, w, CARD_H, true);
		row.onLeft = m.isToggleable() ? m::toggle : expand;
		row.onRight = expand;
		hits.add(row);
		if (hasSettings) {
			Hit arrowHit = new Hit(x + w - (m.isToggleable() ? 70 : 30), y, 24, CARD_H, true);
			arrowHit.onLeft = expand;
			arrowHit.onRight = expand;
			hits.add(arrowHit);
		}

		if (total > CARD_H + 2 && hasSettings) {
			g.enableScissor(x, Math.max(viewTop, y + CARD_H), x + w, Math.min(viewBottom, y + total));
			drawSettings(g, m, x, y + CARD_H, w, a);
			g.disableScissor();
		}
	}

	private void drawSettings(GuiGraphicsExtractor g, Module m, int x, int y, int w, float a) {
		Gfx.roundRect(g, x + 6, y, w - 12, settingsHeight(m) + 2, 5, Gfx.alpha(SETTINGS_BG, a));
		int lx = x + 16;
		int rx = x + w - 16;
		int cy = y + 2;
		for (Setting s : m.settings) {
			int sh = settingHeight(s);
			Hit hit = new Hit(x + 6, cy, w - 12, sh, true);

			if (s instanceof BoolSetting b) {
				Gfx.text(g, b.name, lx, cy + 7, Gfx.alpha(Theme.TEXT, a), false);
				Gfx.roundRect(g, rx - 22, cy + 5, 22, 12, 6, Gfx.alpha(b.value ? Theme.accent() : Theme.TRACK, a));
				Gfx.roundRect(g, rx - 20 + (b.value ? 10 : 0), cy + 7, 8, 8, 4, Gfx.alpha(0xFFFFFFFF, a));
				hit.onLeft = b::toggle;
			} else if (s instanceof ModeSetting ms) {
				Gfx.text(g, ms.name, lx, cy + 7, Gfx.alpha(Theme.TEXT, a), false);
				String v = "< " + ms.get() + " >";
				Gfx.text(g, v, rx - Gfx.width(v), cy + 7, Gfx.alpha(Theme.accent(), a), false);
				hit.onLeft = () -> ms.cycle(1);
				hit.onRight = () -> ms.cycle(-1);
			} else if (s instanceof NumberSetting ns) {
				Gfx.text(g, ns.name, lx, cy + 5, Gfx.alpha(Theme.TEXT, a), false);
				String v = ns.format();
				Gfx.text(g, v, rx - Gfx.width(v), cy + 5, Gfx.alpha(Theme.accent(), a), false);
				int bw = rx - lx;
				int by = cy + 20;
				int fill = Math.round(bw * (float) ns.fraction());
				Gfx.roundRect(g, lx, by, bw, 4, 2, Gfx.alpha(Theme.TRACK, a));
				Gfx.roundRect(g, lx, by, Math.max(3, fill), 4, 2, Gfx.alpha(Theme.accent(), a));
				Gfx.roundRect(g, lx + fill - 4, by - 2, 8, 8, 4, Gfx.alpha(0xFFFFFFFF, a));
				hit.slider = ns;
				hit.sliderX = lx;
				hit.sliderW = bw;
			}
			hits.add(hit);
			cy += sh;
		}
	}

	// ---------------------------------------------------------------- uklad

	private List<Module> visibleModules() {
		if (query.isEmpty()) {
			return NebulaClient.modules().in(Category.values()[tab]);
		}
		String q = query.toLowerCase(Locale.ROOT);
		List<Module> out = new ArrayList<>();
		for (Module m : NebulaClient.modules().all()) {
			if (m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)) {
				out.add(m);
			}
		}
		return out;
	}

	private static String fit(String s, int maxW) {
		if (Gfx.width(s) <= maxW) return s;
		String dots = "...";
		while (s.length() > 1 && Gfx.width(s + dots) > maxW) s = s.substring(0, s.length() - 1);
		return s + dots;
	}

	private static int settingsHeight(Module m) {
		int h = 4;
		for (Setting s : m.settings) h += settingHeight(s);
		return h;
	}

	private static int settingHeight(Setting s) {
		return s instanceof NumberSetting ? 32 : 22;
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
			if (hit.inList && (my < viewTop || my >= viewBottom)) continue;

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
		if (activeSlider != null) {
			updateSlider(event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean changed = activeSlider != null;
		activeSlider = null;
		if (changed) ConfigManager.save(NebulaClient.modules());
		return changed || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTarget -= (float) scrollY * 34f;
		scrollTarget = Math.max(0f, Math.min(scrollTarget, Math.max(0, contentH - (viewBottom - viewTop))));
		return true;
	}

	private void updateSlider(double mouseX) {
		if (activeSlider == null || activeSlider.sliderW <= 0) return;
		NumberSetting s = activeSlider.slider;
		double t = Math.max(0.0, Math.min(1.0, (mouseX - activeSlider.sliderX) / activeSlider.sliderW));
		s.set(s.min + t * (s.max - s.min));
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event.isAllowedChatCharacter() && query.length() < 24) {
			query += event.codepointAsString();
			scroll = 0;
			scrollTarget = 0;
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RSHIFT) {
			onClose();
			return true;
		}
		if (event.key() == InputConstants.KEY_BACKSPACE && !query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			scroll = 0;
			scrollTarget = 0;
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

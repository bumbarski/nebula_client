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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
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
	private static boolean blurBroken;

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
	private boolean world;
	private float dtf = 0.016f;
	private long tabSince = System.nanoTime();
	private float tabY = -1f;
	private final Map<Object, Float> hoverAnim = new HashMap<>();

	private static final int BG_TOP = 0xFF1B1B38;
	private static final int BG_BOT = 0xFF0A0A16;
	private static final int OB_CARD_TOP = 0xFF22223F;
	private static final int OB_CARD_BOT = 0xFF171729;

	private static int vgrad(int top, int bot, int row, int h, float a) {
		return Gfx.alpha(Gfx.lerp(top, bot, row / (float) Math.max(1, h - 1)), a);
	}

	private float hov(Object key, boolean on) {
		float v = hoverAnim.getOrDefault(key, 0f);
		float goal = on ? 1f : 0f;
		v = Theme.animations() ? v + (goal - v) * Math.min(1f, dtf * 14f) : goal;
		hoverAnim.put(key, v);
		return v;
	}

	private float stagger(int index) {
		if (!Theme.obsidian() || !Theme.animations()) return 1f;
		float t = (System.nanoTime() - tabSince) / 1_000_000f - 40f - index * 38f;
		float f = Math.max(0f, Math.min(1f, t / 260f));
		return 1f - (1f - f) * (1f - f) * (1f - f);
	}

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

	/** Tryb panelu w swiecie: okno rysowane przez WorldPanel w wirtualnym rozmiarze w x h. */
	public void forWorld(Minecraft mc, int w, int h) {
		world = true;
		width = w;
		height = h;
	}

	public void drawPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		drawAll(g, mouseX, mouseY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		drawAll(g, mouseX, mouseY);
	}

	private void drawAll(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		dtf = dt;

		float open = Theme.animations() ? Math.min(1f, (now - openedAt) / (Theme.obsidian() ? 360_000_000f : 200_000_000f)) : 1f;
		float a = 1f - (1f - open) * (1f - open) * (1f - open);

		hits.clear();

		// rozmycie tla pomijamy z Irisem (shadery zmieniaja potok renderowania) i po pierwszym bledzie
		if (!world && Theme.glass() && Theme.blur() && !blurBroken && !FabricLoader.getInstance().isModLoaded("iris")) {
			try {
				extractBlurredBackground(g);
			} catch (Throwable t) {
				blurBroken = true;
				NebulaClient.LOGGER.warn("Rozmycie tła menu wyłączone po błędzie: {}", t.toString());
			}
		}
		if (!world) {
			Gfx.gradient(g, 0, 0, width, height,
					Gfx.alpha(0xFF05050A, (Theme.glass() ? 0.28f : 0.50f) * a),
					Gfx.alpha(Gfx.lerp(0xFF05050A, Theme.accentDark(), 0.30f), (Theme.glass() ? 0.45f : 0.66f) * a));
		}

		int winW = Math.min(width - 24, Math.round(500 * Theme.menuSize()));
		int winH = Math.min(height - 24, Math.round(320 * Theme.menuSize()));
		int wx = (width - winW) / 2;
		int wy = (height - winH) / 2 + Math.round((1f - a) * 12f);

		// miekki cien, okno, pasek boczny
		for (int i = 6; i >= 1; i--) {
			Gfx.roundRect(g, wx - i, wy - i, winW + i * 2, winH + i * 2, 8 + i, Gfx.alpha(0xFF000000, 0.07f * a));
		}
		if (Theme.obsidian()) {
			drawObsidianWindow(g, wx, wy, winW, winH, a);
		} else if (Theme.glass()) {
			Gfx.panel(g, wx, wy, winW, winH, 16, a);
			// plywajacy pasek boczny z ciemniejszego szkla
			Gfx.roundRect(g, wx + 6, wy + 6, SIDEBAR - 8, winH - 12, 12, Gfx.alpha(0xFF000000, 0.26f * a));
			Gfx.glassShine(g, wx + 6, wy + 6, SIDEBAR - 8, winH - 12, 12, 0.45f * a);
		} else {
			Gfx.roundRect(g, wx, wy, winW, winH, 8, Gfx.alpha(Theme.PANEL, Math.max(0.94f, Theme.opacity()) * a));
			Gfx.roundRect(g, wx, wy, SIDEBAR + 8, winH, 8, Gfx.alpha(Theme.HEADER, a));
			Gfx.rect(g, wx + SIDEBAR, wy, 8, winH, Gfx.alpha(Theme.PANEL, Math.max(0.94f, Theme.opacity()) * a));
			Gfx.rect(g, wx + SIDEBAR, wy + 8, 1, winH - 16, Gfx.alpha(0xFFFFFFFF, 0.05f * a));
		}

		if (Theme.obsidian()) {
			drawSidebarObsidian(g, wx, wy, winH, mouseX, mouseY, a);
		} else {
			drawSidebar(g, wx, wy, winH, mouseX, mouseY, a);
		}
		drawContent(g, wx + SIDEBAR + 14, wy, winW - SIDEBAR - 28, winH, mouseX, mouseY, dt, a);
	}

	private void drawObsidianWindow(GuiGraphicsExtractor g, int wx, int wy, int winW, int winH, float a) {
		// poswiata w kolorze motywu pod oknem
		for (int i = 10; i >= 1; i--) {
			Gfx.roundRect(g, wx - i * 2, wy - i * 2, winW + i * 4, winH + i * 4, 18 + i * 2, Gfx.alpha(Theme.accent(), 0.012f * a));
		}
		final int wh = winH;
		Gfx.roundRectRows(g, wx, wy, winW, winH, 18, row -> vgrad(BG_TOP, BG_BOT, row, wh, 0.97f * a));
		Gfx.roundOutline(g, wx, wy, winW, winH, 18, row -> Gfx.alpha(Gfx.lerp(Theme.accent(), 0xFFFFFFFF, 0.35f), (0.34f - 0.26f * row / (float) Math.max(1, wh - 1)) * a));
		// jasna kreska na gorze okna
		int hw = Math.min(150, winW / 3);
		Gfx.roundRect(g, wx + (winW - hw) / 2, wy + 1, hw, 2, 1, Gfx.alpha(Gfx.lerp(Theme.accent(), 0xFFFFFFFF, 0.5f), 0.75f * a));
		// ciemny pasek boczny
		Gfx.roundRect(g, wx + 7, wy + 7, SIDEBAR - 9, winH - 14, 13, Gfx.alpha(0xFF05050D, 0.55f * a));
		Gfx.roundOutline(g, wx + 7, wy + 7, SIDEBAR - 9, winH - 14, 13, row -> Gfx.alpha(0xFFFFFFFF, 0.05f * a));
	}

	private void drawSidebarObsidian(GuiGraphicsExtractor g, int wx, int wy, int winH, int mx, int my, float a) {
		// logo z kropka motywu
		Gfx.circle(g, wx + 20, wy + 24, 4.5f, Gfx.alpha(Theme.accent(), 0.30f * a));
		Gfx.circle(g, wx + 20, wy + 24, 2.5f, Gfx.alpha(Gfx.lerp(Theme.accent(), 0xFFFFFFFF, 0.5f), a));
		Gfx.text(g, "NEBULA", wx + 30, wy + 15, Gfx.alpha(Theme.TEXT, a), false);
		Gfx.text(g, "client", wx + 30, wy + 26, Gfx.alpha(Theme.accent(), 0.9f * a), false);

		Category[] cats = Category.values();
		int top = wy + 52;
		int step = 27;
		float goalY = top + tab * step;
		if (tabY < 0f || !Theme.animations()) tabY = goalY;
		tabY += (goalY - tabY) * Math.min(1f, dtf * 16f);
		if (query.isEmpty()) {
			Gfx.roundRect(g, wx + 11, Math.round(tabY), SIDEBAR - 20, 24, 8, Gfx.alpha(Theme.accent(), 0.22f * a));
			Gfx.roundOutline(g, wx + 11, Math.round(tabY), SIDEBAR - 20, 24, 8, row -> Gfx.alpha(Theme.accent(), 0.45f * a));
			Gfx.roundRect(g, wx + 11, Math.round(tabY) + 6, 3, 12, 1, Gfx.alpha(Gfx.lerp(Theme.accent(), 0xFFFFFFFF, 0.4f), a));
		}
		int ty = top;
		for (int i = 0; i < cats.length; i++) {
			Category c = cats[i];
			int count = NebulaClient.modules().in(c).size();
			boolean selected = query.isEmpty() && i == tab;
			boolean hover = mx >= wx + 11 && mx < wx + SIDEBAR - 9 && my >= ty && my < ty + 24;
			float hv = hov("tab" + i, hover);
			if (!selected && hv > 0.01f) {
				Gfx.roundRect(g, wx + 11, ty, SIDEBAR - 20, 24, 8, Gfx.alpha(0xFFFFFFFF, 0.06f * hv * a));
			}
			int tc = selected ? Theme.TEXT : Gfx.lerp(Theme.MUTED, Theme.TEXT, hv);
			Gfx.text(g, c.label, wx + 22 + Math.round(hv * 2f), ty + 8, Gfx.alpha(tc, a), false);
			String n = String.valueOf(count);
			Gfx.text(g, n, wx + SIDEBAR - 18 - Gfx.width(n), ty + 8, Gfx.alpha(Theme.MUTED, 0.65f * a), false);

			Hit hit = new Hit(wx + 8, ty, SIDEBAR - 14, 24, false);
			final int index = i;
			hit.onLeft = () -> {
				tab = index;
				query = "";
				scroll = 0;
				scrollTarget = 0;
				tabSince = System.nanoTime();
			};
			hits.add(hit);
			ty += step;
		}

		int bx = wx + 14;
		int bw = SIDEBAR - 26;
		int by = wy + winH - 36;
		boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + 24;
		float hv = hov("hud", hover);
		Gfx.roundRect(g, bx, by, bw, 24, 8, Gfx.alpha(Gfx.lerp(Theme.accentDark(), Theme.accent(), hv), 0.95f * a));
		Gfx.roundOutline(g, bx, by, bw, 24, 8, row -> Gfx.alpha(0xFFFFFFFF, (0.10f + 0.16f * hv) * a));
		String label = "Edytor HUD";
		Gfx.text(g, label, bx + (bw - Gfx.width(label)) / 2, by + 8, Gfx.alpha(Theme.TEXT, a), false);
		Hit hit = new Hit(bx, by, bw, 24, false);
		hit.onLeft = () -> {
			ConfigManager.save(NebulaClient.modules());
			dev.nebula.client.Compat.setScreen(Minecraft.getInstance(), new HudEditorScreen(this));
		};
		hits.add(hit);
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
			dev.nebula.client.Compat.setScreen(Minecraft.getInstance(), new HudEditorScreen(this));
		};
		hits.add(hit);
	}

	private void drawContent(GuiGraphicsExtractor g, int cx, int wy, int cw, int winH,
							 int mx, int my, float dt, float a) {
		// wyszukiwarka
		if (Theme.obsidian()) {
			Gfx.roundRect(g, cx, wy + 10, cw, 22, 11, Gfx.alpha(0xFF05050D, 0.65f * a));
			final boolean typing = !query.isEmpty();
			Gfx.roundOutline(g, cx, wy + 10, cw, 22, 11, row -> Gfx.alpha(typing ? Theme.accent() : 0xFFFFFFFF, (typing ? 0.55f : 0.07f) * a));
		} else {
			Gfx.roundRect(g, cx, wy + 10, cw, 22, 11, Gfx.alpha(Theme.glass() ? 0xFF000000 : 0xFF12121B, (Theme.glass() ? 0.30f : 1f) * a));
		}
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
				float f = stagger(i);
				drawCard(g, modules.get(i), cx, y + Math.round((1f - f) * 10f), cardW, h, mx, my, dt, a * f);
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

		if (Theme.obsidian()) {
			float hv = hov(m, hover);
			float onT = toggleAnim.getOrDefault(m, on ? 1f : 0f);
			float act = Math.max(onT * 0.6f, hv);
			final int th = Math.max(1, total - 1);
			Gfx.roundRectRows(g, x, y - Math.round(hv), w, total, 12, row -> vgrad(OB_CARD_TOP, OB_CARD_BOT, row, total, (0.90f + 0.08f * hv) * a));
			if (onT > 0.01f && m.isToggleable()) {
				Gfx.roundRect(g, x, y - Math.round(hv), w, total, 12, Gfx.alpha(Theme.accent(), 0.07f * onT * a));
			}
			Gfx.roundOutline(g, x, y - Math.round(hv), w, total, 12,
					row -> Gfx.alpha(Gfx.lerp(0xFFFFFFFF, Theme.accent(), act), (0.07f + 0.30f * act * (1f - 0.55f * row / th)) * a));
		} else if (Theme.glass()) {
			Gfx.roundRect(g, x, y, w, total, 10, Gfx.alpha(0xFFFFFFFF, (hover ? 0.14f : 0.07f) * a));
			final int th = Math.max(1, total - 1);
			Gfx.roundOutline(g, x, y, w, total, 10, row -> Gfx.alpha(0xFFFFFFFF, (0.26f - 0.18f * row / th) * a));
		} else {
			Gfx.roundRect(g, x, y, w, total, 6, Gfx.alpha(hover ? CARD_HOVER : CARD, a));
		}
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

			if (Theme.obsidian()) {
				int sx = x + w - 50;
				int sy = y + 8;
				if (t > 0.02f) Gfx.roundRect(g, sx - 2, sy - 2, 38, 22, 11, Gfx.alpha(Theme.accent(), 0.16f * t * a));
				Gfx.roundRect(g, sx, sy, 34, 18, 9, Gfx.alpha(Gfx.lerp(0xFF2A2A45, Theme.accent(), t), a));
				Gfx.roundRect(g, sx + 2 + Math.round(t * 16f), sy + 2, 14, 14, 7, Gfx.alpha(0xFFFFFFFF, a));
			} else {
				int sx = x + w - 44;
				int sy = y + 9;
				Gfx.roundRect(g, sx, sy, 32, 16, 8, Gfx.alpha(Gfx.lerp(Theme.TRACK, Theme.accent(), t), a));
				Gfx.roundRect(g, sx + 2 + Math.round(t * 16f), sy + 2, 12, 12, 6, Gfx.alpha(0xFFFFFFFF, a));
			}
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
		if (Theme.obsidian()) {
			Gfx.roundRect(g, x + 6, y, w - 12, settingsHeight(m) + 2, 9, Gfx.alpha(0xFF06060E, 0.72f * a));
			Gfx.roundOutline(g, x + 6, y, w - 12, settingsHeight(m) + 2, 9, row -> Gfx.alpha(0xFFFFFFFF, 0.05f * a));
		} else {
			Gfx.roundRect(g, x + 6, y, w - 12, settingsHeight(m) + 2, 8,
					Gfx.alpha(Theme.glass() ? 0xFF000000 : SETTINGS_BG, (Theme.glass() ? 0.28f : 1f) * a));
		}
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
				if (Theme.obsidian()) Gfx.circle(g, lx + fill, by + 2, 7f, Gfx.alpha(Theme.accent(), 0.22f * a));
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
		if (panelClick(event.x(), event.y(), event.button())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	/** Klik w wirtualnych wspolrzednych okna (uzywane tez przez panel w swiecie). */
	public boolean panelClick(double mx, double my, int button) {
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
		return false;
	}

	public void panelDrag(double mx) {
		if (activeSlider != null) updateSlider(mx);
	}

	public void panelRelease() {
		if (activeSlider != null) {
			activeSlider = null;
			ConfigManager.save(NebulaClient.modules());
		}
	}

	public void panelScroll(double amount) {
		scrollTarget -= (float) amount * 34f;
		scrollTarget = Math.max(0f, Math.min(scrollTarget, Math.max(0, contentH - (viewBottom - viewTop))));
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

package dev.nebula.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nebula.client.Compat;
import dev.nebula.client.NebulaClient;
import dev.nebula.client.config.ConfigManager;
import dev.nebula.client.render.Projector;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Tryb "Vision Pro": menu Nebuli jako panel zawieszony w swiecie przed toba. Sterujesz nim celownikiem:
 * patrzysz na przycisk i klikasz LPM (przelacznik/zakladka/suwak) albo PPM (rozwiniecie ustawien),
 * strzalki gora/dol przewijaja liste. Panel stoi w miejscu w swiecie, wiec mozesz sie wokol niego rozgladac.
 */
public final class WorldPanel {
	private WorldPanel() {
	}

	private static final int VW = 440;
	private static final int VH = 290;
	private static final double DISTANCE = 2.4;
	private static final double WIDTH_BLOCKS = 3.6;

	private static ClickGuiScreen screen;
	private static double ax, ay, az;
	private static double rx, rz;
	private static boolean hovered;
	private static float lx, ly;
	private static boolean dragging;
	private static KeyMapping up;
	private static KeyMapping down;

	public static void registerKey(KeyMapping.Category category) {
		up = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nebula.panel_up", InputConstants.Type.KEYSYM, InputConstants.KEY_UP, category));
		down = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nebula.panel_down", InputConstants.Type.KEYSYM, InputConstants.KEY_DOWN, category));
	}

	public static boolean isOpen() {
		return screen != null;
	}

	public static void toggle(Minecraft mc) {
		if (screen != null) {
			close();
		} else {
			open(mc);
		}
	}

	private static void open(Minecraft mc) {
		if (mc.player == null) return;
		Projector p = Projector.create(mc);
		double psi = Math.toRadians(p.yawDeg());
		double th = Math.toRadians(p.pitchDeg());
		double fx = -Math.sin(psi) * Math.cos(th);
		double fy = -Math.sin(th);
		double fz = Math.cos(psi) * Math.cos(th);
		ax = p.cx + fx * DISTANCE;
		ay = p.cy + fy * DISTANCE;
		az = p.cz + fz * DISTANCE;
		// panel zostaje na stale zwrocony tak, jak patrzyles przy otwarciu (nie obraca sie za toba)
		rx = -Math.cos(psi);
		rz = -Math.sin(psi);
		screen = new ClickGuiScreen();
		screen.forWorld(mc, VW, VH);
		hovered = false;
		dragging = false;
	}

	public static void close() {
		if (screen != null) {
			screen.panelRelease();
			ConfigManager.save(NebulaClient.modules());
		}
		screen = null;
		hovered = false;
		dragging = false;
	}

	/** Poczatek ticku: przechwytuje klikniecia zanim gra zacznie bic/stawiac bloki. */
	public static void tick(Minecraft mc) {
		if (up != null) {
			while (up.consumeClick()) {
				if (screen != null && hovered) screen.panelScroll(1);
			}
			while (down.consumeClick()) {
				if (screen != null && hovered) screen.panelScroll(-1);
			}
		}
		if (screen == null) return;
		if (mc.player == null) {
			close();
			return;
		}
		if (Compat.screen(mc) != null) return;
		Projector p = Projector.create(mc);
		if (Math.sqrt((p.cx - ax) * (p.cx - ax) + (p.cy - ay) * (p.cy - ay) + (p.cz - az) * (p.cz - az)) > 24.0) {
			close();
			return;
		}
		if (hovered) {
			while (mc.options.keyAttack.consumeClick()) {
				screen.panelClick(lx, ly, 0);
				dragging = true;
			}
			while (mc.options.keyUse.consumeClick()) {
				screen.panelClick(lx, ly, 1);
			}
			// nie pozwalamy grze bic bloku za panelem, gdy trzymasz przycisk
			mc.options.keyAttack.setDown(false);
			mc.options.keyUse.setDown(false);
		}
		if (dragging) {
			if (mc.mouseHandler.isLeftPressed()) {
				screen.panelDrag(lx);
				mc.options.keyAttack.setDown(false);
			} else {
				dragging = false;
				screen.panelRelease();
			}
		}
	}

	public static void render(GuiGraphicsExtractor g, Minecraft mc) {
		if (screen == null || mc.player == null) return;
		if (Compat.screen(mc) != null) {
			hovered = false;
			return;
		}
		Projector p = Projector.create(mc);
		double w = WIDTH_BLOCKS * Theme.panelSize();
		double h = w * VH / VW;
		double[] tl = new double[2];
		double[] tr = new double[2];
		double[] bl = new double[2];
		double hx = rx * w / 2.0;
		double hz = rz * w / 2.0;
		double hy = h / 2.0;
		if (!p.toScreen(ax - hx, ay + hy, az - hz, tl) || !p.toScreen(ax + hx, ay + hy, az + hz, tr)
				|| !p.toScreen(ax - hx, ay - hy, az - hz, bl)) {
			hovered = false;
			return;
		}
		float m00 = (float) ((tr[0] - tl[0]) / VW);
		float m01 = (float) ((tr[1] - tl[1]) / VW);
		float m10 = (float) ((bl[0] - tl[0]) / VH);
		float m11 = (float) ((bl[1] - tl[1]) / VH);
		double det = m00 * m11 - m10 * m01;
		if (det <= 1e-6) {
			// widzisz panel od tylu albo prawie z boku
			hovered = false;
			return;
		}
		double px = p.sw / 2.0 - tl[0];
		double py = p.sh / 2.0 - tl[1];
		lx = (float) ((px * m11 - py * m10) / det);
		ly = (float) ((py * m00 - px * m01) / det);
		hovered = lx >= 0 && ly >= 0 && lx < VW && ly < VH;

		screen.width = VW;
		screen.height = VH;
		float m20 = (float) tl[0];
		float m21 = (float) tl[1];
		Gfx.pushAffine(g, m00, m01, m10, m11, m20, m21);
		screen.drawPanel(g, hovered ? Math.round(lx) : -1000, hovered ? Math.round(ly) : -1000);
		Gfx.popScale(g);

		// kropka celownika na panelu
		if (hovered) {
			Gfx.circle(g, p.sw / 2f, p.sh / 2f, 3.2f, Gfx.alpha(0xFF000000, 0.45f));
			Gfx.circle(g, p.sw / 2f, p.sh / 2f, 2.2f, 0xFFFFFFFF);
		}
	}
}

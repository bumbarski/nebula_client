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
	private static final double DISTANCE = 3.2;
	private static final double WIDTH_BLOCKS = 4.0;

	private static ClickGuiScreen screen;
	private static double ax, ay, az;
	private static double rx, rz;
	private static double nx, nz;
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
		double fx = -Math.sin(psi);
		double fz = Math.cos(psi);
		ax = p.cx + fx * DISTANCE;
		ay = p.cy - 0.05;
		az = p.cz + fz * DISTANCE;
		// prawa reka panelu = prawa reka kamery przy otwarciu; normalna wskazuje w strone gracza
		rx = -Math.cos(psi);
		rz = -Math.sin(psi);
		nx = -fx;
		nz = -fz;
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

	/** Rzutuje punkt panelu (u w pikselach okna, v w pikselach okna od gory) na ekran; false gdy za kamera. */
	private static boolean project(Projector p, double u, double v, double w, double h, double[] out) {
		double side = (u / VW - 0.5) * w;
		double up = (0.5 - v / VH) * h;
		return p.toScreen(ax + rx * side, ay + up, az + rz * side, out);
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

		// od tylu albo gdy stoisz tuz przy tablicy (lub przechodzisz przez nia) nie rysujemy jej
		double dist = (p.cx - ax) * nx + (p.cz - az) * nz;
		hovered = false;
		if (dist < 0.6) return;

		// trafienie promieniem z celownika w plaszczyzne panelu (jak w modzie kolegi)
		double psi = Math.toRadians(p.yawDeg());
		double th = Math.toRadians(p.pitchDeg());
		double dx = -Math.sin(psi) * Math.cos(th);
		double dy = -Math.sin(th);
		double dz = Math.cos(psi) * Math.cos(th);
		double denom = dx * nx + dz * nz;
		if (denom < -1e-4) {
			double t = ((ax - p.cx) * nx + (az - p.cz) * nz) / denom;
			if (t > 0 && t < 16) {
				double hx = p.cx + dx * t - ax;
				double hy = p.cy + dy * t - ay;
				double hz = p.cz + dz * t - az;
				lx = (float) (((hx * rx + hz * rz) / w + 0.5) * VW);
				ly = (float) ((0.5 - hy / h) * VH);
				hovered = lx >= 0 && ly >= 0 && lx < VW && ly < VH;
			}
		}

		int n = 14;
		double[] a = new double[2];
		double[] b = new double[2];
		if (!project(p, 0, VH / 2.0, w, h, a) || !project(p, VW, VH / 2.0, w, h, b)) {
			hovered = false;
			return;
		}
		double da = p.depth(ax - rx * w / 2, ay, az - rz * w / 2);
		double db = p.depth(ax + rx * w / 2, ay, az + rz * w / 2);
		if (da < 0.3 || db < 0.3) {
			hovered = false;
			return;
		}
		if (Math.abs(da - db) < 0.03 * Math.max(da, db)) n = 1;

		screen.width = VW;
		screen.height = VH;
		int mx = hovered ? Math.round(lx) : -1000;
		int my = hovered ? Math.round(ly) : -1000;

		// perspektywa: panel dzielimy na pionowe paski, kazdy ze swoja skala (os pozioma i pionowa, bez skosu),
		// a przycinamy je scissorem, wiec calosc wyglada jak plaska tablica widziana w perspektywie
		double[] top = new double[2];
		double[] bot = new double[2];
		double[] mid = new double[2];
		double[] mid2 = new double[2];
		int prev = 0;
		boolean ok = project(p, 0, VH / 2.0, w, h, mid);
		for (int i = 0; i < n && ok; i++) {
			double u0 = (double) VW * i / n;
			double u1 = (double) VW * (i + 1) / n;
			ok = project(p, u1, VH / 2.0, w, h, mid2);
			double um = (u0 + u1) / 2.0;
			if (!ok || !project(p, um, 0, w, h, top) || !project(p, um, VH, w, h, bot)) break;
			int x0 = i == 0 ? (int) Math.floor(mid[0]) : prev;
			int x1 = i == n - 1 ? (int) Math.ceil(mid2[0]) : (int) Math.round(mid2[0]);
			prev = x1;
			if (x1 > x0) {
				double sxs = (mid2[0] - mid[0]) / (u1 - u0);
				double hp = bot[1] - top[1];
				if (sxs > 0 && hp > 2) {
					float sy = (float) (hp / VH);
					float yc = (float) ((top[1] + bot[1]) / 2.0);
					g.enableScissor(x0, 0, x1, p.sh);
					Gfx.pushXform(g, (float) (mid[0] - u0 * sxs), yc - VH / 2f * sy, 0f, (float) sxs, sy);
					screen.drawPanel(g, mx, my);
					Gfx.popScale(g);
					g.disableScissor();
				}
			}
			mid[0] = mid2[0];
			mid[1] = mid2[1];
		}

		// kropka celownika na panelu
		if (hovered) {
			Gfx.circle(g, p.sw / 2f, p.sh / 2f, 3.2f, Gfx.alpha(0xFF000000, 0.45f));
			Gfx.circle(g, p.sw / 2f, p.sh / 2f, 2.2f, 0xFFFFFFFF);
		}
	}
}

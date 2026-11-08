package dev.nebula.client.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nebula.client.Compat;
import dev.nebula.client.NebulaClient;
import dev.nebula.client.config.ConfigManager;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.render.Projector;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.setting.Setting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tryb "Vision Pro" (1.21.11): menu Nebuli jako prawdziwy panel 3D rysowany przez GPU w swiecie gry
 * (tak jak u kolegi). Panel stoi w jednym miejscu, nie obraca sie z toba i prawie nic nie kosztuje.
 * Sterowanie: celownik + LPM (przelacznik/zakladka/suwak), PPM na module = rozwiniecie ustawien, strzalki gora/dol = przewijanie.
 */
public final class WorldPanel {
	private WorldPanel() {
	}

	private static final int VW = 440;
	private static final int VH = 290;
	private static final double DISTANCE = 3.2;
	private static final double WIDTH_BLOCKS = 4.0;
	private static final int LIGHT = 0xF000F0;
	private static final int TOP = 0x1A1A34;
	private static final int BOTTOM = 0x0B0B17;
	private static final int ACCENT = 0x9D7CFF;
	private static final int ACCENT2 = 0xDCCFFF;
	private static final int DEEP = 0x6E49C8;
	private static final int VIEW_TOP = 46;
	private static final int VIEW_BOTTOM = 276;

	private static RenderType flat;
	private static boolean open;
	private static double ax, ay, az;
	private static double rx, rz, nx, nz;
	private static long born, closing = -1L, lastNs, pageTime;
	private static float dt;
	private static boolean hovered;
	private static float hu, hv;
	private static boolean landed;
	private static boolean dragging;
	private static NumberSetting dragSetting;
	private static float dragX0, dragW;
	private static int page;
	private static int scroll;
	private static int maxScroll;
	private static final java.util.Set<Module> expanded = new java.util.HashSet<>();
	private static final List<Hit> hits = new ArrayList<>();
	private static final List<Runnable> late = new ArrayList<>();
	private static final Map<String, Float> hoverAnim = new HashMap<>();
	private static Matrix4f m;
	private static VertexConsumer q;
	private static KeyMapping up;
	private static KeyMapping down;

	private record Hit(float x, float y, float w, float h, int button, java.util.function.Consumer<Float> run) {
	}

	public static void registerKey(KeyMapping.Category category) {
		up = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nebula.panel_up", InputConstants.Type.KEYSYM, InputConstants.KEY_UP, category));
		down = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nebula.panel_down", InputConstants.Type.KEYSYM, InputConstants.KEY_DOWN, category));
		WorldRenderEvents.END_MAIN.register(ctx -> {
			Minecraft mc = Minecraft.getInstance();
			if (!open || mc.player == null || ctx.matrices() == null) return;
			MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
			try {
				draw(mc, buf, new Matrix4f(ctx.matrices().last().pose()));
			} catch (Throwable ignored) {
			}
			buf.endBatch();
		});
	}

	/** Stary punkt wejscia z HUD-u: w trybie 3D nic nie rysujemy tutaj. */
	public static void render(GuiGraphics g, Minecraft mc) {
	}

	public static boolean isOpen() {
		return open;
	}

	public static void toggle(Minecraft mc) {
		if (open && closing < 0) {
			close();
		} else if (!open) {
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
		rx = -Math.cos(psi);
		rz = -Math.sin(psi);
		nx = -fx;
		nz = -fz;
		open = true;
		born = System.currentTimeMillis();
		closing = -1L;
		lastNs = System.nanoTime();
		hovered = false;
		dragging = false;
		landed = false;
		page = 0;
		scroll = 0;
		pageTime = born;
		sound(mc, SoundEvents.BEACON_POWER_SELECT, 1.6f);
	}

	public static void close() {
		if (!open || closing >= 0) return;
		ConfigManager.save(NebulaClient.modules());
		closing = System.currentTimeMillis();
		dragging = false;
		hovered = false;
		sound(Minecraft.getInstance(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f);
	}

	private static void sound(Minecraft mc, SoundEvent ev, float pitch) {
		try {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch));
		} catch (Throwable ignored) {
		}
	}

	private static float age() {
		return (float) (System.currentTimeMillis() - born);
	}

	private static float closeAge() {
		return closing < 0 ? -1f : (float) (System.currentTimeMillis() - closing);
	}

	/** Poczatek ticku: przechwytuje klikniecia, zanim gra zacznie bic/stawiac bloki. */
	public static void tick(Minecraft mc) {
		if (up != null) {
			while (up.consumeClick()) {
				if (open && hovered) scrollBy(-1);
			}
			while (down.consumeClick()) {
				if (open && hovered) scrollBy(1);
			}
		}
		if (!open) return;
		if (mc.player == null) {
			open = false;
			return;
		}
		if (closing >= 0 && closeAge() > 950f) {
			open = false;
			return;
		}
		if (closing >= 0 || Compat.screen(mc) != null) return;
		Projector p = Projector.create(mc);
		double dx = p.cx - ax, dy = p.cy - ay, dz = p.cz - az;
		if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 24.0) {
			close();
			return;
		}
		if (hovered) {
			while (mc.options.keyAttack.consumeClick()) {
				click(0);
			}
			while (mc.options.keyUse.consumeClick()) {
				click(1);
			}
			mc.options.keyAttack.setDown(false);
			mc.options.keyUse.setDown(false);
		}
		if (dragging) {
			if (mc.mouseHandler.isLeftPressed() && dragSetting != null) {
				float f = Math.max(0f, Math.min(1f, (hu - dragX0) / dragW));
				dragSetting.set(dragSetting.min + (dragSetting.max - dragSetting.min) * f);
				mc.options.keyAttack.setDown(false);
			} else {
				dragging = false;
				dragSetting = null;
			}
		}
	}

	private static void scrollBy(int d) {
		scroll = Math.max(0, Math.min(maxScroll, scroll + d));
	}

	private static void click(int button) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.button != -1 && h.button != button) continue;
			if (hu >= h.x && hv >= h.y && hu < h.x + h.w && hv < h.y + h.h) {
				h.run.accept(hu);
				return;
			}
		}
	}

	private static boolean over(float x, float y, float w, float h) {
		return hovered && hu >= x && hv >= y && hu < x + w && hv < y + h;
	}

	private static float hvAnim(String id, boolean on) {
		float v = hoverAnim.getOrDefault(id, 0f);
		v += ((on ? 1f : 0f) - v) * Math.min(1f, dt * 14f);
		hoverAnim.put(id, v);
		return v;
	}

	// ---------------------------------------------------------------- rysowanie

	private static RenderType flat() {
		if (flat == null) {
			flat = RenderType.create("nebula_panel", RenderSetup.builder((RenderPipeline) RenderPipelines.DEBUG_QUADS).createRenderSetup());
		}
		return flat;
	}

	private static float clamp(float x) {
		return Math.max(0f, Math.min(1f, x));
	}

	private static float outCubic(float t) {
		float u = 1f - t;
		return 1f - u * u * u;
	}

	private static float inOut(float t) {
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3.0) / 2f;
	}

	private static int argb(int rgb, float a) {
		int al = Math.max(0, Math.min(255, Math.round(a * 255f)));
		return (al << 24) | (rgb & 0xFFFFFF);
	}

	private static int lerp(int a, int b, float t) {
		t = clamp(t);
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static float px(float x) {
		return x - VW / 2f;
	}

	private static float py(float y) {
		return VH / 2f - y;
	}

	private static void vert(float x, float y, float z, int col) {
		q.addVertex((Matrix4fc) m, px(x), py(y), z).setColor(col);
	}

	private static void quad(float x, float y, float w, float h, int cTop, float aTop, int cBot, float aBot, float z) {
		int t = argb(cTop, aTop);
		int b = argb(cBot, aBot);
		vert(x, y, z, t);
		vert(x + w, y, z, t);
		vert(x + w, y + h, z, b);
		vert(x, y + h, z, b);
	}

	private static void band(float x, float y, float w, float h, float oy, float oh, int top, int bot, float a, float z) {
		if (h <= 0f || w <= 0f) return;
		quad(x, y, w, h, lerp(top, bot, (y - oy) / oh), a, lerp(top, bot, (y + h - oy) / oh), a, z);
	}

	private static void round(float x, float y, float w, float h, float r, int top, int bot, float a, float z) {
		if (a <= 0.003f) return;
		r = Math.min(r, Math.min(w, h) / 2f);
		int n = r > 3f ? 5 : 2;
		float step = r / n;
		for (int j = 0; j < n; j++) {
			float yc = r - (j + 0.5f) * step;
			float in = r - (float) Math.sqrt(Math.max(0f, r * r - yc * yc));
			band(x + in, y + j * step, w - 2 * in, step, y, h, top, bot, a, z);
			band(x + in, y + h - (j + 1) * step, w - 2 * in, step, y, h, top, bot, a, z);
		}
		band(x, y + r, w, h - 2 * r, y, h, top, bot, a, z);
	}

	private static void text(String s, float x, float y, float scale, int rgb, float a, float z) {
		if (a <= 0.02f || s.isEmpty()) return;
		Matrix4f tm = new Matrix4f((Matrix4fc) m).translate(px(x), py(y), z).scale(scale, -scale, 1f);
		int col = argb(rgb, a);
		late.add(() -> {
			Minecraft mc = Minecraft.getInstance();
			mc.font.drawInBatch(Component.literal(s), 0f, 0f, col, false, tm, mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, LIGHT);
		});
	}

	private static float tw(String s, float scale) {
		return Minecraft.getInstance().font.width(s) * scale;
	}

	private static void ctext(String s, float cx, float y, float scale, int rgb, float a, float z) {
		text(s, cx - tw(s, scale) / 2f, y, scale, rgb, a, z);
	}

	private static String ellip(String s, float maxPx, float scale) {
		if (tw(s, scale) <= maxPx) return s;
		String t = s;
		while (t.length() > 1 && tw(t + "...", scale) > maxPx) t = t.substring(0, t.length() - 1);
		return t + "...";
	}

	// ---------------------------------------------------------------- scena

	private static void draw(Minecraft mc, MultiBufferSource buffers, Matrix4f base) {
		Projector p = Projector.create(mc);
		long ns = System.nanoTime();
		dt = Math.min(0.1f, (ns - lastNs) / 1.0E9f);
		lastNs = ns;
		float t = age();
		float c = closeAge();

		float fly, slide, content;
		if (c < 0f) {
			fly = inOut(clamp(t / 900f));
			slide = outCubic(clamp((t - 820f) / 460f));
			content = outCubic(clamp((t - 1100f) / 300f));
			if (!landed && t > 880f) {
				landed = true;
				sound(mc, SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f);
			}
		} else {
			content = 1f - clamp(c / 160f);
			slide = 1f - inOut(clamp((c - 120f) / 300f));
			fly = 1f - inOut(clamp((c - 380f) / 480f));
		}

		double psi = Math.toRadians(p.yawDeg());
		double th = Math.toRadians(p.pitchDeg());
		double fdx = -Math.sin(psi) * Math.cos(th);
		double fdy = -Math.sin(th);
		double fdz = Math.cos(psi) * Math.cos(th);
		double udx = -Math.sin(psi) * Math.sin(th);
		double udy = Math.cos(th);
		double udz = Math.cos(psi) * Math.sin(th);

		double w = WIDTH_BLOCKS * Theme.panelSize();
		float k = (float) (w / VW);

		// celowanie: promien z kamery w plaszczyzne panelu
		hovered = false;
		double denom = fdx * nx + fdz * nz;
		if (c < 0f && t > 1200f && denom < -1e-4) {
			double tt = ((ax - p.cx) * nx + (az - p.cz) * nz) / denom;
			if (tt > 0 && tt < 14) {
				double hx = p.cx + fdx * tt - ax;
				double hy = p.cy + fdy * tt - ay;
				double hz = p.cz + fdz * tt - az;
				hu = (float) ((hx * rx + hz * rz) / k + VW / 2.0);
				hv = (float) (VH / 2.0 - hy / k);
				hovered = hu >= 0 && hv >= 0 && hu < VW && hv < VH;
			}
		}

		Matrix4f rot = new Matrix4f((float) rx, 0f, (float) rz, 0f, 0f, 1f, 0f, 0f, (float) nx, 0f, (float) nz, 0f, 0f, 0f, 0f, 1f);
		float ex = (float) (ax - p.cx);
		float ey = (float) (ay - p.cy);
		float ez = (float) (az - p.cz);
		// start lotu: tuz przed kamera, lekko nizej
		float sx = (float) (fdx * 0.9 - udx * 0.3);
		float sy = (float) (fdy * 0.9 - udy * 0.3);
		float sz = (float) (fdz * 0.9 - udz * 0.3);
		float lx = sx + (ex - sx) * fly;
		float ly = sy + (ey - sy) * fly + (float) Math.sin(Math.PI * fly) * 0.45f;
		float lz = sz + (ez - sz) * fly;
		float sc = 0.12f + 0.88f * outCubic(fly);
		float tilt = (1f - fly) * 70f;

		Matrix4f frame = new Matrix4f((Matrix4fc) base).translate(lx, ly, lz).mul(rot).rotateX((float) Math.toRadians(-tilt)).scale(k * sc, k * sc, k * sc);
		m = frame;

		q = buffers.getBuffer(flat());
		hits.clear();
		late.clear();

		if (slide > 0.01f) {
			m = new Matrix4f((Matrix4fc) frame).scale(slide, 1f, 1f);
			float a = Math.min(1f, slide * 1.5f);
			for (int i = 4; i >= 1; i--) {
				round(-i * 2.5f, -i * 2.5f, VW + i * 5f, VH + i * 5f, 22f + i * 2.5f, ACCENT, ACCENT, 0.05f * a, 0f);
			}
			round(-0.8f, -0.8f, VW + 1.6f, VH + 1.6f, 21f, 0xB9A8FF, DEEP, 0.35f * a, 0.2f);
			round(0f, 0f, VW, VH, 20f, TOP, BOTTOM, 0.96f * a, 0.4f);
			round(150f, 0.6f, 140f, 1.6f, 0.8f, ACCENT, ACCENT2, 0.7f * a, 0.8f);
			m = frame;
			if (content > 0.01f) {
				body(content);
			}
		} else {
			// sama kapsula w locie
			round(VW / 2f - 36f, VH / 2f - 8f, 72f, 16f, 8f, ACCENT, DEEP, 0.95f, 0.4f);
		}

		// kropka celownika na panelu
		if (hovered && content > 0.5f) {
			round(hu - 4f, hv - 4f, 8f, 8f, 4f, ACCENT, ACCENT, 0.35f, 2.6f);
			round(hu - 2.2f, hv - 2.2f, 4.4f, 4.4f, 2.2f, 0xFFFFFF, 0xFFFFFF, 0.95f, 2.8f);
		}

		if (buffers instanceof MultiBufferSource.BufferSource bs) {
			bs.endBatch(flat());
		}
		for (Runnable r : late) r.run();
	}

	private static void go(int p) {
		if (p != page) {
			page = p;
			scroll = 0;
			pageTime = System.currentTimeMillis();
		}
	}

	private record Row(int type, Module mod, Setting set, float h) {
	}

	private static List<Row> rows() {
		List<Row> out = new ArrayList<>();
		Category cat = Category.values()[page];
		for (Module mo : NebulaClient.modules().in(cat)) {
			out.add(new Row(0, mo, null, 28f));
			if (expanded.contains(mo)) {
				for (Setting s : mo.settings) {
					float h = s instanceof NumberSetting ? 32f : 22f;
					out.add(new Row(1, mo, s, h));
				}
			}
		}
		return out;
	}

	private static void body(float a) {
		float z = 1.0f;
		text("NEBULA", 26f, 20f, 1.5f, 0xFFFFFF, a, z);
		text("Right Shift = panel", 26f, 36f, 0.6f, 0x8F8AB0, a * 0.8f, z);
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			final int idx = i;
			float x = 14f, y = 58f + i * 32f, w = 96f, h = 26f;
			boolean on = page == i;
			float hvv = hvAnim("tab" + i, over(x, y, w, h));
			float ap = a * outCubic(clamp((System.currentTimeMillis() - born - 1100f - i * 70f) / 300f));
			if (on || hvv > 0.01f) {
				round(x, y, w, h, 9f, on ? ACCENT : 0x2A2A4C, on ? DEEP : 0x1E1E38, (on ? 0.9f : 0.6f * hvv) * ap, z);
			}
			text(cats[i].label, x + 14f, y + 9f, 0.95f, on ? 0xFFFFFF : lerp(0xA9A5C8, 0xFFFFFF, hvv), ap, z + 0.4f);
			hits.add(new Hit(x, y, w, h, -1, f -> go(idx)));
		}

		long pa = System.currentTimeMillis() - pageTime;
		text(cats[page].label, 126f, 22f, 1.3f, 0xFFFFFF, a, z);

		List<Row> rows = rows();
		maxScroll = 0;
		float acc = 0f;
		for (int i = rows.size() - 1; i >= 0; i--) {
			acc += rows.get(i).h + 4f;
			if (acc > VIEW_BOTTOM - VIEW_TOP) {
				maxScroll = i + 1;
				break;
			}
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll));

		float y = VIEW_TOP;
		int shown = 0;
		for (int i = scroll; i < rows.size(); i++) {
			Row r = rows.get(i);
			if (y + r.h > VIEW_BOTTOM) break;
			float prog = outCubic(clamp((pa - shown * 28f) / 260f));
			float ar = a * prog;
			float xo = (1f - prog) * 14f;
			if (r.type == 0) drawModule(r.mod, 126f + xo, y, 276f, r.h, ar, z);
			else drawSetting(r.set, 140f + xo, y, 262f, r.h, ar, z);
			y += r.h + 4f;
			shown++;
		}

		if (maxScroll > 0) {
			scrollBtn(412f, VIEW_TOP, "^", -1, a, z);
			scrollBtn(412f, VIEW_BOTTOM - 20f, "v", 1, a, z);
		}
	}

	private static void scrollBtn(float x, float y, String s, int d, float a, float z) {
		float hvv = hvAnim("sc" + d, over(x, y, 20f, 20f));
		round(x, y, 20f, 20f, 7f, lerp(0x2A2A4C, ACCENT, hvv), lerp(0x1E1E38, DEEP, hvv), 0.9f * a, z);
		ctext(s, x + 10f, y + 6f, 0.9f, 0xFFFFFF, a, z + 0.4f);
		hits.add(new Hit(x, y, 20f, 20f, -1, f -> scrollBy(d)));
	}

	private static void toggleSwitch(float x, float y, boolean on, float a, float z) {
		float f = on ? 1f : 0f;
		round(x, y, 30f, 14f, 7f, lerp(0x3A3A5C, ACCENT, f), lerp(0x2A2A44, DEEP, f), 0.95f * a, z);
		round(x + 2f + (on ? 16f : 0f), y + 2f, 10f, 10f, 5f, 0xFFFFFF, 0xDCDCEE, a, z + 0.3f);
	}

	private static void drawModule(Module mo, float x, float y, float w, float h, float a, float z) {
		float hvv = hvAnim("m" + mo.id, over(x, y, w, h));
		boolean on = mo.isEnabled();
		round(x, y, w, h, 9f, lerp(0x23233F, 0x2E2E55, hvv), lerp(0x191930, 0x222244, hvv), 0.95f * a, z);
		if (on) round(x, y + 6f, 2.5f, h - 12f, 1.2f, ACCENT, ACCENT2, a, z + 0.3f);
		text(ellip(mo.name, 160f, 1f), x + 12f, y + 6f, 1f, 0xFFFFFF, a, z + 0.4f);
		text(ellip(mo.description, 190f, 0.55f), x + 12f, y + 17.5f, 0.55f, 0x8F8AB0, a * 0.9f, z + 0.4f);
		if (mo.isToggleable()) {
			toggleSwitch(x + w - 40f, y + 7f, on, a, z + 0.4f);
			hits.add(new Hit(x, y, w - 50f, h, 0, f -> mo.toggle()));
		}
		if (!mo.settings.isEmpty()) {
			boolean ex = expanded.contains(mo);
			float bx = x + w - (mo.isToggleable() ? 62f : 24f);
			float cv = hvAnim("c" + mo.id, over(bx, y + 4f, 20f, h - 8f));
			round(bx, y + 6f, 16f, 16f, 6f, lerp(0x2E2E52, ACCENT, cv), lerp(0x232344, DEEP, cv), 0.9f * a, z + 0.4f);
			ctext(ex ? "-" : "+", bx + 8f, y + 10f, 0.9f, 0xFFFFFF, a, z + 0.7f);
			hits.add(new Hit(bx, y, 20f, h, 0, f -> toggleExpand(mo)));
			hits.add(new Hit(x, y, w, h, 1, f -> toggleExpand(mo)));
		}
	}

	private static void toggleExpand(Module mo) {
		if (!expanded.remove(mo)) expanded.add(mo);
	}

	private static void drawSetting(Setting s, float x, float y, float w, float h, float a, float z) {
		float hvv = hvAnim("s" + System.identityHashCode(s), over(x, y, w, h));
		round(x, y, w, h, 7f, lerp(0x1D1D36, 0x27274A, hvv), lerp(0x15152A, 0x1B1B38, hvv), 0.95f * a, z);
		text(ellip(s.name, 150f, 0.85f), x + 10f, y + (h > 28f ? 5f : 7f), 0.85f, 0xDADAF0, a, z + 0.4f);
		if (s instanceof BoolSetting b) {
			toggleSwitch(x + w - 40f, y + 4f, b.value, a, z + 0.3f);
			hits.add(new Hit(x, y, w, h, 0, f -> b.toggle()));
		} else if (s instanceof ModeSetting ms) {
			String v = "< " + ms.get() + " >";
			text(ellip(v, 120f, 0.85f), x + w - 10f - Math.min(120f, tw(v, 0.85f)), y + 7f, 0.85f, ACCENT2, a, z + 0.4f);
			hits.add(new Hit(x + w / 2f, y, w / 2f, h, 0, f -> ms.cycle(1)));
			hits.add(new Hit(x + w / 2f, y, w / 2f, h, 1, f -> ms.cycle(-1)));
		} else if (s instanceof NumberSetting ns) {
			String v = ns.format();
			text(v, x + w - 10f - tw(v, 0.85f), y + 5f, 0.85f, ACCENT2, a, z + 0.4f);
			float bx = x + 10f, bw = w - 20f, by = y + 21f;
			round(bx, by, bw, 4f, 2f, 0x3A3A5C, 0x2A2A44, 0.95f * a, z + 0.3f);
			float fw = Math.max(4f, (float) (bw * ns.fraction()));
			round(bx, by, fw, 4f, 2f, ACCENT, DEEP, a, z + 0.5f);
			round(bx + fw - 4f, by - 2f, 8f, 8f, 4f, 0xFFFFFF, 0xDCDCEE, a, z + 0.7f);
			hits.add(new Hit(bx - 4f, y, bw + 8f, h, 0, f -> {
				dragging = true;
				dragSetting = ns;
				dragX0 = bx;
				dragW = bw;
				float fr = Math.max(0f, Math.min(1f, (f - bx) / bw));
				ns.set(ns.min + (ns.max - ns.min) * fr);
			}));
		} else {
			String note = "(w oknie: Right Ctrl)";
			text(note, x + w - 10f - tw(note, 0.7f), y + 8f, 0.7f, 0x8F8AB0, a, z + 0.4f);
		}
	}
}

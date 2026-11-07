package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.render.Projector;
import dev.nebula.client.render.WorldOverlay;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.NumberSetting;
import dev.nebula.client.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Sus chunk: wykrywa pod ziemia chunki z w pelni wyrosnietymi ametystami (AMETHYST_CLUSTER) i pokazuje ich
 * obrys na wysokosci markera (domyslnie 60). Gdy jestes wyzej, marker plynnie podaza za wysokoscia gracza.
 */
public final class SusChunkModule extends Module implements WorldOverlay {
	private final NumberSetting minCount = add(new NumberSetting("Minimum ametystów w chunku", 1, 20, 2, 1));
	private final NumberSetting scanTop = add(new NumberSetting("Skanuj do wysokości Y", -32, 64, 30, 2));
	private final NumberSetting chunkRange = add(new NumberSetting("Zasięg (chunki)", 3, 16, 10, 1));
	private final NumberSetting baseY = add(new NumberSetting("Wysokość markera", 0, 120, 60, 1));
	private final BoolSetting labels = add(new BoolSetting("Napisy", true));
	private final BoolSetting tracers = add(new BoolSetting("Tracer do chunka", false));

	private final Map<Long, Integer> sus = new HashMap<>();
	private int cursor;
	private double planeY = Double.NaN;
	private long lastFrame;

	public SusChunkModule() {
		super("suschunk", "Sus chunk", "Chunki z wyrośniętymi ametystami pod ziemią, z markerem na Y=60", Category.VISUAL, false);
	}

	@Override
	protected void onDisable() {
		sus.clear();
		planeY = Double.NaN;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		int r = chunkRange.getInt();
		int side = 2 * r + 1;
		int pcx = mc.player.getBlockX() >> 4;
		int pcz = mc.player.getBlockZ() >> 4;

		// usun chunki spoza zasiegu
		sus.keySet().removeIf(k -> Math.abs(cx(k) - pcx) > r || Math.abs(cz(k) - pcz) > r);

		// jeden chunk na tick, po kolei po calym kwadracie wokol gracza
		cursor = (cursor + 1) % (side * side);
		int cxk = pcx - r + cursor % side;
		int czk = pcz - r + cursor / side;
		long key = key(cxk, czk);
		LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cxk, czk);
		if (chunk == null) {
			sus.remove(key);
			return;
		}
		int top = scanTop.getInt();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int count = 0;
		for (int y = -64; y <= top; y++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					if (chunk.getBlockState(pos.set((cxk << 4) + x, y, (czk << 4) + z)).is(Blocks.AMETHYST_CLUSTER)) count++;
				}
			}
		}
		if (count >= minCount.getInt()) sus.put(key, count); else sus.remove(key);
	}

	@Override
	public void drawWorld(GuiGraphics g, Minecraft mc, Projector p) {
		if (sus.isEmpty()) return;

		// marker na Y=60, a gdy gracz jest wyzej, plynnie na poziomie jego stop (bez skokow po blokach)
		long now = System.nanoTime();
		double dt = lastFrame == 0 ? 0 : Math.min(0.2, (now - lastFrame) / 1e9);
		lastFrame = now;
		double target = Math.max(baseY.get(), p.cy - 1.62);
		if (Double.isNaN(planeY)) planeY = target;
		planeY += (target - planeY) * (1 - Math.exp(-dt * 9.0));
		double y = planeY;

		int color = 0xFFC77DFF;
		int soft = 0x66C77DFF;
		double[] s = new double[2];
		for (Map.Entry<Long, Integer> e : sus.entrySet()) {
			double x0 = cx(e.getKey()) * 16.0;
			double z0 = cz(e.getKey()) * 16.0;
			double x1 = x0 + 16;
			double z1 = z0 + 16;
			p.line(g, x0, y, z0, x1, y, z0, color);
			p.line(g, x1, y, z0, x1, y, z1, color);
			p.line(g, x1, y, z1, x0, y, z1, color);
			p.line(g, x0, y, z1, x0, y, z0, color);
			// kratka co 4 bloki
			for (int i = 4; i < 16; i += 4) {
				p.line(g, x0 + i, y, z0, x0 + i, y, z1, soft);
				p.line(g, x0, y, z0 + i, x1, y, z0 + i, soft);
			}
			double mx = x0 + 8;
			double mz = z0 + 8;
			if (tracers.value) p.tracer(g, mx, y, mz, 0xAAC77DFF);
			if (labels.value && p.toScreen(mx, y, mz, s)) {
				double dx = mx - p.cx, dz = mz - p.cz;
				String t = "SUS " + e.getValue() + "  " + Math.round(Math.sqrt(dx * dx + dz * dz)) + "m";
				Gfx.text(g, t, (int) s[0] - Gfx.width(t) / 2, (int) s[1] - 4, 0xFFFFFFFF, true);
			}
		}
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	private static int cx(long k) {
		return (int) (k >> 32);
	}

	private static int cz(long k) {
		return (int) k;
	}
}

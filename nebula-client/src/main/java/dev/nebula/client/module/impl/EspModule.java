package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.render.Projector;
import dev.nebula.client.render.WorldOverlay;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wspolna baza ESP: skanuje okolice gracza warstwami (po jednej-dwie na tick, zeby nie przycinac gry)
 * i rysuje ramki oraz tracery nakladka 2D, wiec cele widac przez bloki.
 */
public abstract class EspModule extends Module implements WorldOverlay {
	private static final int MAX_FOUND = 6000;

	protected final NumberSetting range = add(new NumberSetting("Range", 8, 64, 32, 4));
	protected final NumberSetting maxCount = add(new NumberSetting("Maks. liczba celów", 20, 400, 200, 20));
	protected final BoolSetting tracers = add(new BoolSetting("Tracery", true));

	private record Hit(int x, int y, int z, int color) {
	}

	private final Map<Block, Integer> colorCache = new HashMap<>();
	private List<Hit> hits = List.of();
	private List<Hit> building = new ArrayList<>();
	private List<Hit> sorted = List.of();
	private int sortAge;
	private int layer = -1;
	private int ox, oy, oz;
	private String key = "";

	protected EspModule(String id, String name, String description) {
		super(id, name, description, Category.VISUAL, false);
	}

	/** Kolor ARGB dla bloku o danej nazwie (sciezka bez "minecraft:") albo 0, gdy to nie jest cel. */
	protected abstract int classify(String blockPath);

	/** Tekst zmieniajacy sie razem z ustawieniami celow; zmiana czysci wyniki skanowania. */
	protected String settingsKey() {
		return "";
	}

	@Override
	protected void onDisable() {
		hits = List.of();
		sorted = List.of();
		building = new ArrayList<>();
		layer = -1;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		String k = settingsKey() + "|" + range.getInt();
		if (!k.equals(key)) {
			key = k;
			colorCache.clear();
			hits = List.of();
			sorted = List.of();
			building = new ArrayList<>();
			layer = -1;
		}

		final int r = range.getInt();
		if (layer < 0) {
			layer = 0;
			ox = mc.player.getBlockX();
			oy = mc.player.getBlockY();
			oz = mc.player.getBlockZ();
			building = new ArrayList<>();
		}

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int y0 = Math.max(-64, oy - r);
		int y1 = Math.min(320, oy + r);
		for (int n = 0; n < 2 && layer <= 2 * r; n++, layer++) {
			int x = ox - r + layer;
			for (int y = y0; y <= y1; y++) {
				for (int z = oz - r; z <= oz + r; z++) {
					BlockState st = mc.level.getBlockState(pos.set(x, y, z));
					if (st.isAir()) continue;
					Block b = st.getBlock();
					Integer c = colorCache.get(b);
					if (c == null) {
						c = classify(BuiltInRegistries.BLOCK.getKey(b).getPath());
						colorCache.put(b, c);
					}
					if (c != 0 && building.size() < MAX_FOUND) building.add(new Hit(x, y, z, c));
				}
			}
		}
		if (layer > 2 * r) {
			hits = building;
			sortAge = 99;
			layer = -1;
		}
	}

	@Override
	public void drawWorld(GuiGraphicsExtractor g, Minecraft mc, Projector p) {
		if (hits.isEmpty()) return;
		if (++sortAge > 10) {
			sortAge = 0;
			List<Hit> copy = new ArrayList<>(hits);
			copy.sort(Comparator.comparingDouble(h -> dist2(h, p)));
			int cap = maxCount.getInt();
			sorted = copy.size() > cap ? new ArrayList<>(copy.subList(0, cap)) : copy;
		}
		boolean tr = tracers.value;
		for (Hit h : sorted) {
			int color = h.color();
			p.box(g, h.x, h.y, h.z, h.x + 1, h.y + 1, h.z + 1, color);
			if (tr) p.tracer(g, h.x + 0.5, h.y + 0.5, h.z + 0.5, (color & 0x00FFFFFF) | 0xAA000000);
		}
	}

	private static double dist2(Hit h, Projector p) {
		double dx = h.x + 0.5 - p.cx, dy = h.y + 0.5 - p.cy, dz = h.z + 0.5 - p.cz;
		return dx * dx + dy * dy + dz * dz;
	}
}

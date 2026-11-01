package dev.nebula.client.module.impl;

import dev.nebula.client.NebulaClient;
import dev.nebula.client.setting.ModeSetting;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Block ESP: podswietla wybrane bloki przez sciany. Zestawy gotowe albo "Własne":
 * plik config/nebula/esp_blocks.txt (jeden blok na linie, np. "diamond_ore #4EE3F5", wildcard: "*_ore", "deepslate_*").
 */
public final class BlockEspModule extends EspModule {
	private static final String[] MODES = {"Rudy", "Cenne", "Struktury", "Własne"};
	private static final Set<String> VALUABLE = Set.of("diamond_ore", "deepslate_diamond_ore", "emerald_ore",
			"deepslate_emerald_ore", "ancient_debris", "spawner", "trial_spawner", "vault", "ominous_vault");
	private static final Set<String> STRUCTURE = Set.of("spawner", "trial_spawner", "vault", "ominous_vault",
			"end_portal_frame", "end_portal", "budding_amethyst", "amethyst_cluster", "sculk_shrieker",
			"suspicious_sand", "suspicious_gravel", "reinforced_deepslate");

	private final ModeSetting mode = add(new ModeSetting("Zestaw", MODES, 0));

	private record Custom(String pattern, int color) {
	}

	private List<Custom> custom = List.of();
	private long customStamp = -1;

	public BlockEspModule() {
		super("blockesp", "Block ESP", "Podświetla wybrane bloki przez ściany (rudy, spawnery, własne)");
	}

	@Override
	protected String settingsKey() {
		if (mode.index() == 3) {
			loadCustom();
			return mode.get() + customStamp;
		}
		return mode.get();
	}

	@Override
	protected int classify(String path) {
		return switch (mode.index()) {
			case 0 -> path.endsWith("_ore") || path.equals("ancient_debris") ? color(path) : 0;
			case 1 -> VALUABLE.contains(path) ? color(path) : 0;
			case 2 -> STRUCTURE.contains(path) ? color(path) : 0;
			default -> {
				for (Custom c : custom) {
					if (matches(c.pattern, path)) yield c.color;
				}
				yield 0;
			}
		};
	}

	private static int color(String path) {
		if (path.contains("diamond")) return 0xFF4EE3F5;
		if (path.contains("emerald")) return 0xFF17DD62;
		if (path.contains("gold")) return 0xFFFCEE4B;
		if (path.contains("iron")) return 0xFFD8AF93;
		if (path.contains("redstone")) return 0xFFFF2F2F;
		if (path.contains("lapis")) return 0xFF345EC3;
		if (path.contains("coal")) return 0xFF8A8A8A;
		if (path.contains("copper")) return 0xFFE77C56;
		if (path.contains("ancient")) return 0xFFC0453B;
		if (path.contains("spawner") || path.contains("vault")) return 0xFFB03CFF;
		if (path.contains("amethyst")) return 0xFFC77DFF;
		return 0xFFFFFFFF;
	}

	private static boolean matches(String pattern, String path) {
		if (pattern.startsWith("*") && pattern.endsWith("*") && pattern.length() > 2) {
			return path.contains(pattern.substring(1, pattern.length() - 1));
		}
		if (pattern.startsWith("*")) return path.endsWith(pattern.substring(1));
		if (pattern.endsWith("*")) return path.startsWith(pattern.substring(0, pattern.length() - 1));
		return path.equals(pattern);
	}

	/** Wczytuje plik wlasnych blokow, gdy sie zmienil (tworzy przykladowy, jesli go nie ma). */
	private void loadCustom() {
		try {
			Path file = FabricLoader.getInstance().getConfigDir().resolve("nebula").resolve("esp_blocks.txt");
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, String.join("\n",
						"# Własne bloki dla Block ESP. Jedna linia = jeden blok (nazwa z Minecrafta, bez 'minecraft:').",
						"# Opcjonalnie kolor po spacji, np. #FF00AA. Gwiazdka działa jako wildcard: *_ore, deepslate_*, *chest*",
						"diamond_ore #4EE3F5",
						"deepslate_diamond_ore #4EE3F5",
						"ancient_debris #C0453B",
						"") );
			}
			long stamp = Files.getLastModifiedTime(file).toMillis();
			if (stamp == customStamp) return;
			List<Custom> out = new ArrayList<>();
			for (String raw : Files.readAllLines(file)) {
				String line = raw.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] parts = line.split("\\s+");
				String id = parts[0].toLowerCase(Locale.ROOT);
				if (id.startsWith("minecraft:")) id = id.substring(10);
				int color = 0xFFFFFFFF;
				if (parts.length > 1 && parts[1].startsWith("#") && parts[1].length() == 7) {
					try {
						color = 0xFF000000 | Integer.parseInt(parts[1].substring(1), 16);
					} catch (NumberFormatException ignored) {
					}
				}
				out.add(new Custom(id, color));
			}
			custom = out;
			customStamp = stamp;
		} catch (Exception e) {
			NebulaClient.LOGGER.warn("Block ESP: nie udało się wczytać esp_blocks.txt: {}", e.toString());
		}
	}
}

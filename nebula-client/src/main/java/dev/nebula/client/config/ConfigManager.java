package dev.nebula.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nebula.client.NebulaClient;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.module.Module;
import dev.nebula.client.module.ModuleManager;
import dev.nebula.client.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Zapis i odczyt ustawien do config/nebula.json. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ConfigManager() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("nebula.json");
	}

	public static void load(ModuleManager modules) {
		Path file = path();
		if (!Files.exists(file)) return;
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject saved = root.getAsJsonObject("modules");
			if (saved == null) return;

			for (Module m : modules.all()) {
				JsonObject o = saved.getAsJsonObject(m.id);
				if (o == null) continue;

				if (m.isToggleable() && o.has("enabled")) {
					m.setEnabled(o.get("enabled").getAsBoolean());
				}
				if (m instanceof HudModule hud) {
					if (o.has("x")) hud.x = o.get("x").getAsInt();
					if (o.has("y")) hud.y = o.get("y").getAsInt();
				}
				JsonObject s = o.getAsJsonObject("settings");
				if (s != null) {
					for (Setting setting : m.settings) setting.load(s);
				}
			}
		} catch (IOException | RuntimeException e) {
			NebulaClient.LOGGER.warn("Nie udało się wczytać nebula.json, używam domyślnych ustawień", e);
		}
	}

	public static void save(ModuleManager modules) {
		JsonObject root = new JsonObject();
		JsonObject saved = new JsonObject();

		for (Module m : modules.all()) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", m.isEnabled());
			if (m instanceof HudModule hud) {
				o.addProperty("x", hud.x);
				o.addProperty("y", hud.y);
			}
			JsonObject s = new JsonObject();
			for (Setting setting : m.settings) setting.save(s);
			o.add("settings", s);
			saved.add(m.id, o);
		}
		root.add("modules", saved);

		try {
			Path file = path();
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			NebulaClient.LOGGER.warn("Nie udało się zapisać nebula.json", e);
		}
	}
}

package dev.nebula.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wersja 1.21.11: tekst na niebie i pasek Spotify pochodza z modu LyricsFlow (autor: 374m / cmonmanx, uzyte za jego zgoda),
 * wbudowanego w ten jar bez zmian. Przy pierwszym uruchomieniu zapisujemy mu konfiguracje z wlaczonym tylko tekstem na niebie
 * i paskiem muzyki. Dalsze ustawienia zmieniasz w jego menu w grze (domyslnie klawisz K albo kropka).
 */
public final class NebulaPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		try {
			if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
			Path dir = FabricLoader.getInstance().getConfigDir().resolve("lyricsflow");
			Path cfg = dir.resolve("config.json");
			if (Files.exists(cfg)) return;
			Files.createDirectories(dir);
			String json = "{\n"
					+ "  \"configVersion\": 21,\n"
					+ "  \"enabled\": true,\n"
					+ "  \"lyricsOn\": true,\n"
					+ "  \"showLyrics\": true,\n"
					+ "  \"lyricsStyle\": \"SKY\",\n"
					+ "  \"showTopBar\": true,\n"
					+ "  \"topBarStyle\": \"KARAOKE\",\n"
					+ "  \"skyPreset\": \"off\",\n"
					+ "  \"fxSkyColor\": false,\n"
					+ "  \"fxWeather\": false,\n"
					+ "  \"fxAurora\": false,\n"
					+ "  \"fxStars\": false,\n"
					+ "  \"stars\": false,\n"
					+ "  \"motes\": false,\n"
					+ "  \"streaks\": false,\n"
					+ "  \"skyAlways\": false,\n"
					+ "  \"songChime\": false,\n"
					+ "  \"customHotbar\": false,\n"
					+ "  \"chroma\": false,\n"
					+ "  \"hudKeys\": false,\n"
					+ "  \"hudCps\": false,\n"
					+ "  \"hudPlayer\": false,\n"
					+ "  \"fpsBoost\": false\n"
					+ "}\n";
			Files.writeString(cfg, json, StandardCharsets.UTF_8);
		} catch (Throwable ignored) {
			// brak konfiguracji = mod kolegi uzyje swoich ustawien domyslnych
		}
	}
}

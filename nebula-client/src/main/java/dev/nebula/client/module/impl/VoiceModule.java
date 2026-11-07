package dev.nebula.client.module.impl;

import dev.nebula.client.NebulaClient;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.NumberSetting;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Glosowe powitanie przy starcie gry i pozegnanie przy zamknieciu.
 * Najpierw szuka wlasnych plikow config/nebula/start.wav i stop.wav, potem nagran wbudowanych w jar
 * (assets/nebula/voice/*.wav), a jesli ich nie ma, czyta tekst glosem systemowym Windows (rosyjski glos zenski, np. Microsoft Irina).
 */
public final class VoiceModule extends Module {
	/** Tekst po rosyjsku oraz zapis fonetyczny dla glosow bez rosyjskiego (polskie i angielskie). */
	private record Phrase(String ru, String pl, String en) {
	}

	private static final Phrase GREETING = new Phrase("Добро пожаловать в «Небулу»",
			"Dobro pożałowat w Nebulu", "Dobro pozhalovat v Nebulu");
	private static final Phrase FAREWELL = new Phrase("Пошёл ты на хуй, сукин сын",
			"Paszoł ty na huj, sukin syn", "Pashol ty na khuy, sookin sin");

	private final BoolSetting onStart = add(new BoolSetting("Powitanie przy starcie", true));
	private final BoolSetting onStop = add(new BoolSetting("Pożegnanie przy zamknięciu", true));
	private final NumberSetting volume = add(new NumberSetting("Głośność", 10, 100, 80, 5));

	public VoiceModule() {
		super("voice", "Głos powitalny", "Kobiecy głos po rosyjsku przy starcie i zamknięciu gry", Category.GAME, true);
	}

	/** Po starcie gry: gra w tle, nie blokuje ladowania. */
	public void greet() {
		if (!isEnabled() || !onStart.value) return;
		Thread t = new Thread(() -> play("start", GREETING, false), "nebula-voice");
		t.setDaemon(true);
		t.start();
	}

	/** Przy zamykaniu: czeka na koniec nagrania (maks. kilka sekund), zeby gra nie uciela glosu. */
	public void farewell() {
		if (!isEnabled() || !onStop.value) return;
		play("stop", FAREWELL, true);
	}

	private void play(String name, Phrase text, boolean wait) {
		try {
			Path custom = FabricLoader.getInstance().getConfigDir().resolve("nebula").resolve(name + ".wav");
			java.io.InputStream bundled = VoiceModule.class.getResourceAsStream("/assets/nebula/voice/" + name + ".wav");
			if (Files.isRegularFile(custom)) {
				if (bundled != null) bundled.close();
				try (AudioInputStream in = AudioSystem.getAudioInputStream(custom.toFile())) {
					playStream(in, wait);
				}
			} else if (bundled != null) {
				try (java.io.InputStream raw = new java.io.BufferedInputStream(bundled);
					 AudioInputStream in = AudioSystem.getAudioInputStream(raw)) {
					playStream(in, wait);
				}
			} else if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
				speakWindows(text, wait);
			} else {
				NebulaClient.LOGGER.info("Głos: brak pliku {} (poza Windows potrzebny własny plik wav)", custom);
			}
		} catch (Exception e) {
			NebulaClient.LOGGER.warn("Głos: {}", e.toString());
		}
	}

	private void playStream(AudioInputStream in, boolean wait) throws Exception {
		{
			Clip clip = AudioSystem.getClip();
			clip.open(in);
			if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
				float db = (float) (20.0 * Math.log10(Math.max(0.01, volume.get() / 100.0)));
				gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
			}
			CountDownLatch done = new CountDownLatch(1);
			clip.addLineListener(ev -> {
				if (ev.getType() == LineEvent.Type.STOP) done.countDown();
			});
			clip.start();
			if (wait) {
				done.await(10, TimeUnit.SECONDS);
			} else {
				done.await(60, TimeUnit.SECONDS);
			}
			clip.close();
		}
	}

	private void speakWindows(Phrase text, boolean wait) throws Exception {
		// Kolejnosc: rosyjski glos zenski, dowolny rosyjski, polski zenski (zapis fonetyczny), jakikolwiek zenski/inny (zapis angielski)
		String script = String.join("\n",
				"Add-Type -AssemblyName System.Speech",
				"$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
				"$all = @($s.GetInstalledVoices() | Where-Object { $_.Enabled })",
				"$say = '" + q(text.ru()) + "'",
				"$v = $all | Where-Object { $_.VoiceInfo.Culture.Name -like 'ru*' -and $_.VoiceInfo.Gender -eq 'Female' } | Select-Object -First 1",
				"if (-not $v) { $v = $all | Where-Object { $_.VoiceInfo.Culture.Name -like 'ru*' } | Select-Object -First 1 }",
				"if (-not $v) { $say = '" + q(text.pl()) + "'; $v = $all | Where-Object { $_.VoiceInfo.Culture.Name -like 'pl*' -and $_.VoiceInfo.Gender -eq 'Female' } | Select-Object -First 1 }",
				"if (-not $v) { $say = '" + q(text.pl()) + "'; $v = $all | Where-Object { $_.VoiceInfo.Culture.Name -like 'pl*' } | Select-Object -First 1 }",
				"if (-not $v) { $say = '" + q(text.en()) + "'; $v = $all | Where-Object { $_.VoiceInfo.Gender -eq 'Female' } | Select-Object -First 1 }",
				"if (-not $v) { $say = '" + q(text.en()) + "'; $v = $all | Select-Object -First 1 }",
				"if (-not $v) { exit 3 }",
				"$s.SelectVoice($v.VoiceInfo.Name)",
				"$s.Volume = " + volume.getInt(),
				"[Console]::Out.WriteLine('VOICE ' + $v.VoiceInfo.Name + ' ' + $v.VoiceInfo.Culture.Name)",
				"$s.Speak($say)");
		String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
		Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-EncodedCommand", encoded).redirectErrorStream(true).start();
		boolean finished = p.waitFor(wait ? 12 : 30, TimeUnit.SECONDS);
		if (!finished) {
			p.destroy();
			NebulaClient.LOGGER.warn("Głos: PowerShell nie skończył mówić w czasie");
			return;
		}
		String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
		if (p.exitValue() == 3) {
			NebulaClient.LOGGER.warn("Głos: Windows nie ma żadnego głosu syntezatora (Ustawienia > Czas i język > Mowa), "
					+ "wrzuć własne pliki config/nebula/start.wav i stop.wav");
		} else if (p.exitValue() != 0) {
			NebulaClient.LOGGER.warn("Głos: błąd PowerShell (kod {}): {}", p.exitValue(), out);
		} else {
			NebulaClient.LOGGER.info("Głos: {}", out.lines().filter(l -> l.startsWith("VOICE")).findFirst().orElse("ok"));
		}
	}

	private static String q(String s) {
		return s.replace("'", "''");
	}
}

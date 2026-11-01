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
 * Najpierw szuka wlasnych plikow config/nebula/start.wav i stop.wav, a jesli ich nie ma,
 * czyta tekst glosem systemowym Windows (rosyjski glos zenski, np. Microsoft Irina).
 */
public final class VoiceModule extends Module {
	private static final String GREETING = "Приветствия на «Небуле»";
	private static final String FAREWELL = "Пошёл ты на хуй, сукин сын";

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

	private void play(String name, String text, boolean wait) {
		try {
			Path custom = FabricLoader.getInstance().getConfigDir().resolve("nebula").resolve(name + ".wav");
			if (Files.isRegularFile(custom)) {
				playWav(custom, wait);
			} else if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
				speakWindows(text, wait);
			} else {
				NebulaClient.LOGGER.info("Głos: brak pliku {} (poza Windows potrzebny własny plik wav)", custom);
			}
		} catch (Exception e) {
			NebulaClient.LOGGER.warn("Głos: {}", e.toString());
		}
	}

	private void playWav(Path file, boolean wait) throws Exception {
		try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
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

	private void speakWindows(String text, boolean wait) throws Exception {
		String script = String.join("\n",
				"Add-Type -AssemblyName System.Speech",
				"$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
				"$all = @($s.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -like 'ru*' })",
				"$v = $all | Where-Object { $_.VoiceInfo.Gender -eq 'Female' } | Select-Object -First 1",
				"if (-not $v) { $v = $all | Select-Object -First 1 }",
				"if (-not $v) { exit 3 }",
				"$s.SelectVoice($v.VoiceInfo.Name)",
				"$s.Volume = " + volume.getInt(),
				"$s.Speak('" + text.replace("'", "''") + "')");
		String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
		Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-EncodedCommand", encoded).redirectErrorStream(true).start();
		boolean finished = p.waitFor(wait ? 10 : 30, TimeUnit.SECONDS);
		if (!finished) {
			p.destroy();
			return;
		}
		if (p.exitValue() == 3) {
			NebulaClient.LOGGER.warn("Głos: w Windows nie ma rosyjskiego głosu (Ustawienia > Czas i język > Mowa > Dodaj głosy > rosyjski), "
					+ "albo wrzuć własne pliki config/nebula/start.wav i stop.wav");
		}
	}
}

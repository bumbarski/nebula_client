package dev.nebula.client.module;

import dev.nebula.client.module.impl.AppearanceModule;
import dev.nebula.client.module.impl.ArmorModule;
import dev.nebula.client.module.impl.BlockEspModule;
import dev.nebula.client.module.impl.BlockOutlineModule;
import dev.nebula.client.module.impl.SkyLyricsModule;
import dev.nebula.client.module.impl.StorageEspModule;
import dev.nebula.client.module.impl.SusChunkModule;
import dev.nebula.client.module.impl.GammaModule;
import dev.nebula.client.module.impl.ItemCooldownModule;
import dev.nebula.client.module.impl.MusicModule;
import dev.nebula.client.module.impl.ViewModelModule;
import dev.nebula.client.module.impl.VoiceModule;
import dev.nebula.client.module.impl.ClockModule;
import dev.nebula.client.module.impl.CoordsModule;
import dev.nebula.client.module.impl.CpsModule;
import dev.nebula.client.module.impl.FpsModule;
import dev.nebula.client.module.impl.FreelookModule;
import dev.nebula.client.module.impl.ToggleKeysModule;
import dev.nebula.client.module.impl.KeystrokesModule;
import dev.nebula.client.module.impl.PingModule;
import dev.nebula.client.module.impl.PotionModule;
import dev.nebula.client.module.impl.WatermarkModule;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
	private final List<Module> all = new ArrayList<>();

	private final FreelookModule freelook = new FreelookModule();
	private final VoiceModule voice = new VoiceModule();

	public VoiceModule voice() {
		return voice;
	}

	public FreelookModule freelook() {
		return freelook;
	}

	public ModuleManager() {
		// kolejnosc = kolejnosc w ClickGUI
		register(new FpsModule());
		register(new CpsModule());
		register(new PingModule());
		register(new CoordsModule());
		register(new ClockModule());
		register(new KeystrokesModule());
		register(new ArmorModule());
		register(new PotionModule());
		register(new ItemCooldownModule());
		register(new MusicModule());
		register(new SkyLyricsModule());

		register(new AppearanceModule());
		register(new WatermarkModule());
		register(new BlockOutlineModule());
		register(new BlockEspModule());
		register(new StorageEspModule());
		register(new SusChunkModule());
		register(new GammaModule());
		register(new ViewModelModule());

		register(new dev.nebula.client.module.impl.KillAuraModule());
		register(new dev.nebula.client.module.impl.NukerModule());
		register(new dev.nebula.client.module.impl.FlightModule());
		register(new dev.nebula.client.module.impl.NoPushModule());
		register(new dev.nebula.client.module.impl.SpeedModule());

		register(new dev.nebula.client.module.impl.AutoArmorModule());
		register(new dev.nebula.client.module.impl.ChestStealerModule());
		register(new dev.nebula.client.module.impl.SpammerModule());

		register(new dev.nebula.client.module.impl.CriticalsModule());
		register(new dev.nebula.client.module.impl.OffHandModule());
		register(new dev.nebula.client.module.impl.AntiAimModule());
		register(new dev.nebula.client.module.impl.NoDelayModule());
		register(new dev.nebula.client.module.impl.AutoToolModule());
		register(new dev.nebula.client.module.impl.SpeedMineModule());
		register(new dev.nebula.client.module.impl.AutoEzModule());
		register(new dev.nebula.client.module.impl.NameProtectModule());

		register(new dev.nebula.client.module.impl.FreecamModule());
		register(new dev.nebula.client.module.impl.ChamsModule());
		register(new dev.nebula.client.module.impl.AutoTrapModule());

		register(new dev.nebula.client.module.impl.BlinkModule());
		register(new dev.nebula.client.module.impl.NoCameraClipModule());
		register(new dev.nebula.client.module.impl.AttributeSwapModule());
		register(new dev.nebula.client.module.impl.TridentBoostModule());
		register(new dev.nebula.client.module.impl.LegitStrafeModule());
		register(new dev.nebula.client.module.impl.PopArrowsModule());
		register(new dev.nebula.client.module.impl.GuiMoveModule());

		register(new dev.nebula.client.module.impl.FakePlayerModule());

		register(freelook);
		register(voice);
		register(new ToggleKeysModule());
	}

	private void register(Module module) {
		all.add(module);
	}

	public List<Module> all() {
		return Collections.unmodifiableList(all);
	}

	public List<Module> in(Category category) {
		List<Module> out = new ArrayList<>();
		for (Module m : all) {
			if (m.category == category) out.add(m);
		}
		return out;
	}

	public List<HudModule> hud() {
		List<HudModule> out = new ArrayList<>();
		for (Module m : all) {
			if (m instanceof HudModule h) out.add(h);
		}
		return out;
	}

	public void tick(Minecraft mc) {
		for (Module m : all) {
			if (m.isEnabled()) m.onTick(mc);
		}
	}
}

package dev.nebula.client.module;

import dev.nebula.client.module.impl.AppearanceModule;
import dev.nebula.client.module.impl.ArmorModule;
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

		register(new AppearanceModule());
		register(new WatermarkModule());

		register(freelook);
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

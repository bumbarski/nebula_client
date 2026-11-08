package dev.nebula.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nebula.client.config.ConfigManager;
import dev.nebula.client.hud.HudManager;
import dev.nebula.client.module.ModuleManager;
import dev.nebula.client.ui.ClickGuiScreen;
import dev.nebula.client.ui.Theme;
import dev.nebula.client.ui.WorldPanel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NebulaClient implements ClientModInitializer {
	public static final String MOD_ID = "nebula";
	public static final Logger LOGGER = LoggerFactory.getLogger("Nebula");

	private static ModuleManager modules;
	private static KeyMapping openGuiKey;
	private static KeyMapping classicGuiKey;

	public static ModuleManager modules() {
		return modules;
	}

	@Override
	public void onInitializeClient() {
		modules = new ModuleManager();
		ConfigManager.load(modules);

		String category = "key.category.nebula.main";
		openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.nebula.open_gui", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));
		classicGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.nebula.open_gui_classic", InputConstants.Type.KEYSYM, InputConstants.KEY_RCONTROL, category));
		modules.freelook().registerKey(category);
		WorldPanel.registerKey(category);

		ClientTickEvents.START_CLIENT_TICK.register(WorldPanel::tick);

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openGuiKey.consumeClick()) {
				if (mc.player != null && Compat.screen(mc) == null) {
					Compat.setScreen(mc, new ClickGuiScreen());
				}
			}
			if (mc.player != null) {
				modules.tick(mc);
			}
		});

		HudRenderCallback.EVENT.register((graphics, tickCounter) -> HudManager.render(graphics));

		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> modules.voice().greet());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			ConfigManager.save(modules);
			modules.voice().farewell();
		});
		LOGGER.info("Nebula Client załadowany");
	}
}

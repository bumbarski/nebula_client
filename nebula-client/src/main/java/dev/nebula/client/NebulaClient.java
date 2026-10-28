package dev.nebula.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nebula.client.config.ConfigManager;
import dev.nebula.client.hud.HudManager;
import dev.nebula.client.module.ModuleManager;
import dev.nebula.client.ui.ClickGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NebulaClient implements ClientModInitializer {
	public static final String MOD_ID = "nebula";
	public static final Logger LOGGER = LoggerFactory.getLogger("Nebula");

	private static ModuleManager modules;
	private static KeyMapping openGuiKey;

	public static ModuleManager modules() {
		return modules;
	}

	@Override
	public void onInitializeClient() {
		modules = new ModuleManager();
		ConfigManager.load(modules);

		// Klawisz otwierajacy menu: domyslnie Prawy Shift (do zmiany w Sterowaniu)
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.nebula.open_gui",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_RSHIFT,
				category
		));

		modules.freelook().registerKey(category);

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openGuiKey.consumeClick()) {
				if (mc.player != null && dev.nebula.client.Compat.screen(mc) == null) {
					dev.nebula.client.Compat.setScreen(mc, new ClickGuiScreen());
				}
			}
			if (mc.player != null) {
				modules.tick(mc);
			}
		});

		// HUD rysowany tuz przed czatem
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
				(graphics, deltaTracker) -> HudManager.render(graphics)
		);

		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> ConfigManager.save(modules));
		LOGGER.info("Nebula Client załadowany");
	}
}

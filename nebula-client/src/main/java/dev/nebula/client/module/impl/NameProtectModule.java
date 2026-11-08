package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Podmienia twoj nick w wiadomosciach czatu na "Nebula" (przydatne do nagran). */
public final class NameProtectModule extends Module {
	private static NameProtectModule self;

	public NameProtectModule() {
		super("nameprotect", "Name Protect", "Ukrywa twój nick w czacie", Category.VISUAL, false);
		self = this;
		ClientReceiveMessageEvents.MODIFY_GAME.register((msg, overlay) -> {
			Minecraft mc = Minecraft.getInstance();
			if (self == null || !self.isEnabled() || mc.player == null) return msg;
			String name = mc.player.getName().getString();
			String s = msg.getString();
			return s.contains(name) ? Component.literal(s.replace(name, "Nebula")) : msg;
		});
	}
}

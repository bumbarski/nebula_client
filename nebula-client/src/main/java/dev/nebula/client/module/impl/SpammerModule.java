package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.ModeSetting;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** Wysyla na czat jedna z gotowych wiadomosci co kilka sekund (np. "ez"). */
public final class SpammerModule extends Module {
	private final ModeSetting text = add(new ModeSetting("Message", new String[]{"ez", "gg", "Nebula Client on top", "xD"}, 0));
	private final NumberSetting delay = add(new NumberSetting("Delay (seconds)", 2, 60, 8, 1));
	private int ticks;

	public SpammerModule() {
		super("spammer", "Spammer", "Sends a chat message every few seconds", Category.GAME, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || Compat.screen(mc) != null) return;
		if (++ticks < delay.getInt() * 20) return;
		ticks = 0;
		mc.player.connection.sendChat(text.get());
	}
}

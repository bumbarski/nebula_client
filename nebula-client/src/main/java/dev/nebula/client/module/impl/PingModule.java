package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;

public final class PingModule extends HudModule {
	public PingModule() {
		super("ping", "Ping", "Opóźnienie do serwera", Category.HUD, false, 4, 64);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		String value = "-";
		if (mc.player != null && mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (info != null) {
				value = info.getLatency() + " ms";
			}
		}
		drawRows(g, List.<String[]>of(new String[]{"Ping", value}));
	}
}

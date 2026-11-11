package dev.nebula.client.module.impl;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/** Mozesz sie ruszac (WASD, spacja, shift) z otwartym ekwipunkiem i innymi oknami. */
public final class GuiMoveModule extends Module {
	public GuiMoveModule() {
		super("guimove", "GUI Move", "Move while a GUI is open", Category.MOVEMENT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		var screen = Compat.screen(mc);
		if (mc.player == null || screen == null || screen instanceof ChatScreen || screen.getClass().getSimpleName().contains("Sign") || screen.getClass().getSimpleName().contains("Anvil")) return;
		for (KeyMapping k : new KeyMapping[]{mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight, mc.options.keyJump, mc.options.keySprint}) {
			int code = Reflect.keyCode(k);
			if (code >= 0) k.setDown(Reflect.keyDown(mc, code));
		}
	}
}

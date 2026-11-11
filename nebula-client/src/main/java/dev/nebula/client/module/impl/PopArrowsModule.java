package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import net.minecraft.client.Minecraft;

/** Usuwa wbite w ciebie strzaly (nie zaslaniaja juz modelu postaci). */
public final class PopArrowsModule extends Module {
	public PopArrowsModule() {
		super("poparrows", "Pop Arrows", "Hides arrows stuck in your body", Category.VISUAL, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player != null && mc.player.getArrowCount() > 0) mc.player.setArrowCount(0);
	}
}

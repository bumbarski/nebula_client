package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Gdy cel, ktorego ostatnio uderzyles, zginie, wysyla wiadomosc na czat. */
public final class AutoEzModule extends Module {
	private final ModeSetting text = add(new ModeSetting("Message", new String[]{"ez", "gg", "ez Nebula Client", "xD"}, 0));
	private LivingEntity target;

	public AutoEzModule() {
		super("autoez", "Auto EZ", "Writes in chat after you kill a target", Category.GAME, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) return;
		LivingEntity last = mc.player.getLastHurtMob();
		if (last != null) target = last;
		if (target != null && !target.isAlive() && mc.player.tickCount - mc.player.getLastHurtMobTimestamp() < 100) {
			mc.player.connection.sendChat(text.get());
			target = null;
		}
	}
}

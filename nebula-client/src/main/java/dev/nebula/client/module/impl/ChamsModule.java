package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Chams: podswietla graczy (i opcjonalnie moby) konturem widocznym przez sciany. */
public final class ChamsModule extends Module {
	private final BoolSetting mobs = add(new BoolSetting("Także moby", false));

	public ChamsModule() {
		super("chams", "Chams", "Kontur graczy widoczny przez ściany", Category.VISUAL, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player) continue;
			if (e instanceof Player || (mobs.value && e instanceof LivingEntity)) e.setGlowingTag(true);
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e != mc.player) e.setGlowingTag(false);
		}
	}
}

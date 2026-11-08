package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.ModeSetting;
import net.minecraft.client.Minecraft;

/** Criticals — logika przeniesiona z ThunderHack Recode (GPL-3.0, pan4ur). Pakiety pozycji tuz przed atakiem. */
public final class CriticalsModule extends Module {
	public static CriticalsModule instance;
	public static boolean cancelCrit;
	private final ModeSetting mode = add(new ModeSetting("Tryb", new String[]{"Ncp", "Strict", "OldNCP", "UpdatedNCP", "Grim"}, 0));

	public CriticalsModule() {
		super("criticals", "Criticals", "Krytyki przez pakiety pozycji przed atakiem", Category.COMBAT, false);
		instance = this;
	}

	/** Wywolywane tuz przed atakiem (z mixina MultiPlayerGameMode.attack). */
	public static void onAttack() {
		if (instance != null && instance.isEnabled() && !cancelCrit) instance.doCrit();
	}

	public void doCrit() {
		Minecraft mc = Minecraft.getInstance();
		var p = mc.player;
		if (p == null || mc.level == null) return;
		boolean grim = mode.index() == 4;
		if ((p.onGround() || p.getAbilities().flying || grim) && !p.isInLava() && !p.isUnderWater()) {
			switch (mode.index()) {
				case 2 -> {
					pos(0.00001058293536);
					pos(0.00000916580235);
					pos(0.00000010371854);
				}
				case 0 -> {
					pos(0.0625D);
					pos(0.);
				}
				case 3 -> {
					pos(0.000000271875);
					pos(0.);
				}
				case 1 -> {
					pos(0.062600301692775);
					pos(0.07260029960661);
					pos(0.);
					pos(0.);
				}
				case 4 -> {
					if (!p.onGround()) pos(-0.000001);
				}
			}
		}
	}

	private static void pos(double dy) {
		dev.nebula.client.Reflect.sendPos(Minecraft.getInstance(), dy);
	}
}

package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** Mocniejszy zryw z trojzebem z Riptide. */
public final class TridentBoostModule extends Module {
	private final NumberSetting boost = add(new NumberSetting("Mnożnik", 1.0, 3.0, 1.5, 0.1));

	public TridentBoostModule() {
		super("tridentboost", "Trident Boost", "Wzmacnia zryw trójzębu Riptide", Category.MOVEMENT, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || !mc.player.isAutoSpinAttack()) return;
		double m = 1.0 + (boost.get() - 1.0) * 0.15;
		mc.player.setDeltaMovement(mc.player.getDeltaMovement().scale(m));
	}
}

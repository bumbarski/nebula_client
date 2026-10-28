package dev.nebula.client.module.impl;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.ModeSetting;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Rozgladanie sie jak w F5, ale gracz dalej idzie w swoja strone. */
public final class FreelookModule extends Module {
	/** Stan czytany przez mixiny. */
	public static boolean active;
	public static float yaw;
	public static float pitch;

	private final ModeSetting mode = add(new ModeSetting("Tryb klawisza", new String[]{"Przytrzymaj", "Przełącz"}, 0));
	private final BoolSetting thirdPerson = add(new BoolSetting("Widok z trzeciej osoby", true));

	private KeyMapping key;
	private CameraType saved;

	public FreelookModule() {
		super("freelook", "Free look", "Obracasz kamerę jak w F5, a postać idzie dalej prosto", Category.GAME, true);
	}

	public void registerKey(KeyMapping.Category category) {
		key = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.nebula.freelook", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, category));
	}

	@Override
	public void onTick(Minecraft mc) {
		if (key == null || mc.player == null) return;
		boolean hold = mode.index() == 0;
		if (hold) {
			boolean down = key.isDown();
			while (key.consumeClick()) {
				// zuzyj klikniecia, zeby nie zostaly na pozniej
			}
			if (dev.nebula.client.Compat.screen(mc) != null) down = false;
			set(mc, down);
		} else {
			boolean pressed = false;
			while (key.consumeClick()) pressed = !pressed;
			if (dev.nebula.client.Compat.screen(mc) != null) pressed = false;
			if (pressed) set(mc, !active);
		}
	}

	private void set(Minecraft mc, boolean on) {
		if (on == active) return;
		if (on) {
			yaw = mc.player.getYRot();
			pitch = mc.player.getXRot();
			if (thirdPerson.value) {
				saved = mc.options.getCameraType();
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
			active = true;
		} else {
			active = false;
			if (saved != null) {
				mc.options.setCameraType(saved);
				saved = null;
			}
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (active && mc != null && mc.player != null) set(mc, false);
		active = false;
	}
}

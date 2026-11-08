package dev.nebula.client.module.impl;

import dev.nebula.client.Compat;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Freecam: kamera odlacza sie od postaci i lata swobodnie, a postac stoi w miejscu. */
public final class FreecamModule extends Module {
	private final NumberSetting speed = add(new NumberSetting("Prędkość", 0.2, 3.0, 0.8, 0.1));
	private ArmorStand cam;

	public FreecamModule() {
		super("freecam", "Freecam", "Latająca kamera, postać zostaje w miejscu", Category.VISUAL, false);
		ClientTickEvents.START_CLIENT_TICK.register(this::startTick);
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		cam = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
		cam.setPos(mc.player.getX(), mc.player.getEyeY() - 1.0, mc.player.getZ());
		cam.setYRot(mc.player.getYRot());
		cam.setXRot(mc.player.getXRot());
		cam.setOldPosAndRot();
		mc.setCameraEntity(cam);
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.setCameraEntity(mc.player);
		cam = null;
	}

	private void startTick(Minecraft mc) {
		if (cam == null || !isEnabled()) return;
		if (mc.player == null || mc.level == null) {
			setEnabled(false);
			return;
		}
		if (Compat.screen(mc) != null) return;
		double yaw = Math.toRadians(mc.player.getYRot());
		double pitch = Math.toRadians(mc.player.getXRot());
		double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
		double s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
		double v = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
		double sp = speed.get();
		double mx = (-Math.sin(yaw) * Math.cos(pitch) * f + Math.cos(yaw) * s) * sp;
		double my = (-Math.sin(pitch) * f + v) * sp;
		double mz = (Math.cos(yaw) * Math.cos(pitch) * f + Math.sin(yaw) * s) * sp;
		cam.setOldPosAndRot();
		cam.setPos(cam.getX() + mx, cam.getY() + my, cam.getZ() + mz);
		// postac stoi w miejscu: wylaczamy klawisze ruchu zanim gra je odczyta
		for (KeyMapping k : new KeyMapping[]{mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight, mc.options.keyJump, mc.options.keyShift, mc.options.keySprint}) {
			k.setDown(false);
		}
	}

	@Override
	public void onFrame(Minecraft mc) {
		if (cam == null || mc.player == null) return;
		cam.setYRot(mc.player.getYRot());
		cam.setXRot(mc.player.getXRot());
		cam.yRotO = cam.getYRot();
		cam.xRotO = cam.getXRot();
	}
}

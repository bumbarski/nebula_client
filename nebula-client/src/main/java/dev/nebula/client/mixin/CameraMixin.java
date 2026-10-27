package dev.nebula.client.mixin;

import dev.nebula.client.module.impl.FreelookModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	/** Podczas free looka kamera dostaje wlasny obrot, a gracz dalej patrzy (i idzie) w swoja strone. */
	@ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float nebula$yaw(float yaw) {
		if (!FreelookModule.active) return yaw;
		var player = Minecraft.getInstance().player;
		return player == null ? yaw : yaw + (FreelookModule.yaw - player.getYRot());
	}

	@ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float nebula$pitch(float pitch) {
		if (!FreelookModule.active) return pitch;
		var player = Minecraft.getInstance().player;
		return player == null ? pitch : pitch + (FreelookModule.pitch - player.getXRot());
	}
}

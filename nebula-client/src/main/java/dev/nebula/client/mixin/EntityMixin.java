package dev.nebula.client.mixin;

import dev.nebula.client.module.impl.FreelookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/** Ruch myszy kreci kamera zamiast graczem, gdy free look jest aktywny. */
	@Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
	private void nebula$turn(double dx, double dy, CallbackInfo ci) {
		if (FreelookModule.active && (Object) this == Minecraft.getInstance().player) {
			FreelookModule.yaw += (float) dx * 0.15f;
			FreelookModule.pitch = Math.max(-90f, Math.min(90f, FreelookModule.pitch + (float) dy * 0.15f));
			ci.cancel();
		}
	}
}

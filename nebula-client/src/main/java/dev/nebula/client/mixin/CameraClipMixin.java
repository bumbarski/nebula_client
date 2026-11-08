package dev.nebula.client.mixin;

import dev.nebula.client.module.impl.NoCameraClipModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraClipMixin {
	/** Kamera w trzeciej osobie nie przyblizy sie do postaci, gdy za plecami jest sciana. */
	@Inject(method = "getMaxZoom(F)F", at = @At("HEAD"), cancellable = true)
	private void nebula$zoom(float max, CallbackInfoReturnable<Float> cir) {
		if (NoCameraClipModule.active) cir.setReturnValue(max);
	}
}

package dev.nebula.client.mixin;

import dev.nebula.client.module.impl.NoPushModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class NoPushMixin {
	/** Wypychanie gracza z wnetrza blokow (po perle, tlokach itp.). */
	@Inject(method = "moveTowardsClosestSpace(DD)V", at = @At("HEAD"), cancellable = true)
	private void nebula$push(double x, double z, CallbackInfo ci) {
		if (NoPushModule.active) ci.cancel();
	}
}

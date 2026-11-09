package dev.nebula.client.mixin;

import dev.nebula.client.module.impl.NoPushModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityPushMixin {
	@Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
	private void nebula$push(double x, double y, double z, CallbackInfo ci) {
		if (NoPushModule.active && (Object) this == Minecraft.getInstance().player) ci.cancel();
	}
}

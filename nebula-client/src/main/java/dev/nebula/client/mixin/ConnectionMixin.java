package dev.nebula.client.mixin;

import dev.nebula.client.module.impl.BlinkModule;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
	@ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), argsOnly = true)
	private Packet<?> nebula$rot(Packet<?> packet) {
		return dev.nebula.client.util.SilentRot.apply(packet);
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void nebula$send(Packet<?> packet, CallbackInfo ci) {
		if (BlinkModule.intercept(packet)) ci.cancel();
	}
}

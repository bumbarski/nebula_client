package dev.nebula.client.mixin;

import dev.nebula.client.module.impl.GammaModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionInstance.class)
public abstract class OptionInstanceMixin {
	/** Opcja gamma zwraca wartosc z modulu (suwak w opcjach gry nadal pokazuje swoja). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "get", at = @At("RETURN"), cancellable = true)
	private void nebula$gamma(CallbackInfoReturnable cir) {
		GammaModule m = GammaModule.instance;
		if (m == null || !m.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) return;
		if ((Object) this == mc.options.gamma()) cir.setReturnValue(m.value());
	}
}

package dev.nebula.client.mixin;

import dev.nebula.client.module.impl.BlockOutlineModule;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	/** 1.21.4: kolor obrysu to ostatni argument int metody renderHitOutline. */
	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int nebula$color(int color) {
		BlockOutlineModule m = BlockOutlineModule.instance;
		if (m == null || !m.isEnabled()) return color;
		return m.color();
	}
}

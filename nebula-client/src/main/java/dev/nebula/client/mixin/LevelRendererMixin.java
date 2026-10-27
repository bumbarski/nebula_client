package dev.nebula.client.mixin;

import dev.nebula.client.module.impl.BlockOutlineModule;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	// Obrys rysuja dwa wywolania: czarna otoczka (kolor czarny, grubosc 7) i wlasciwa linia.
	// Zmieniamy tylko ta druga.
	@ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int nebula$color(int color) {
		BlockOutlineModule m = BlockOutlineModule.instance;
		if (m == null || !m.isEnabled() || color == 0xFF000000) return color;
		return m.color();
	}

	@ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float nebula$width(float width) {
		BlockOutlineModule m = BlockOutlineModule.instance;
		if (m == null || !m.isEnabled() || width == 7.0f) return width;
		return m.width();
	}
}

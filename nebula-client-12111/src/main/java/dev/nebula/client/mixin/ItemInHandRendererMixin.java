package dev.nebula.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nebula.client.module.impl.ViewModelModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("HEAD"))
	private void nebula$before(AbstractClientPlayer player, float a, float b, InteractionHand hand, float c,
							   ItemStack stack, float d, PoseStack pose, SubmitNodeCollector collector, int light,
							   CallbackInfo ci) {
		ViewModelModule m = ViewModelModule.instance;
		if (m == null || !m.isEnabled()) return;
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		pose.pushPose();
		pose.translate(m.x.get() * side, m.y.get(), m.z.get());
		float s = m.scale.getFloat();
		pose.scale(s, s, s);
		pose.mulPose(Axis.XP.rotationDegrees(m.rotX.getFloat()));
		pose.mulPose(Axis.YP.rotationDegrees(m.rotY.getFloat() * side));
		pose.mulPose(Axis.ZP.rotationDegrees(m.rotZ.getFloat() * side));
	}

	@Inject(method = {"submitArmWithItem", "renderArmWithItem"}, at = @At("RETURN"))
	private void nebula$after(AbstractClientPlayer player, float a, float b, InteractionHand hand, float c,
							  ItemStack stack, float d, PoseStack pose, SubmitNodeCollector collector, int light,
							  CallbackInfo ci) {
		ViewModelModule m = ViewModelModule.instance;
		if (m == null || !m.isEnabled()) return;
		pose.popPose();
	}
}

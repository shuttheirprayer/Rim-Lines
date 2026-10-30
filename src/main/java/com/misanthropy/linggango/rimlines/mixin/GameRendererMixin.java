package com.misanthropy.linggango.rimlines.mixin;

import com.misanthropy.linggango.rimlines.RimRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void rimlines$afterHand(float partialTick, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        RimRenderer.renderHand();
    }
}

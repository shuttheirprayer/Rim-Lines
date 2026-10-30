package com.misanthropy.linggango.rimlines.mixin;

import com.misanthropy.linggango.rimlines.RimRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin {

    @Inject(method = "createBuffers", at = @At("TAIL"))
    private void rimlines$rebuilt(int width, int height, boolean clearError, CallbackInfo ci) {
        RimRenderer.targetRebuilt();
    }
}

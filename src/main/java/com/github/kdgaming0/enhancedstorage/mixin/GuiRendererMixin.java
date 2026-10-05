package com.github.kdgaming0.enhancedstorage.mixin;

import com.github.kdgaming0.enhancedstorage.gui.layer.PageLayerCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

//? if <26.2 {
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.GameRenderer;
 *///?}

/**
 * Hooks for {@link PageLayerCache}: renders pending page layers right before the main GUI, and points the layer's
 * own renderer at the layer target instead of the main render target.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    //? if <26.2 {
    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

    @Shadow
    @Final
    private SubmitNodeCollector submitNodeCollector;
    //?}

    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    //? if <26.2 {
    @Inject(method = "render", at = @At("HEAD"))
    private void enhancedstorage$renderPageLayers(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        if (PageLayerCache.isLayerRenderer((GuiRenderer) (Object) this)) return;
        PageLayerCache.renderPending(state -> new GuiRenderer(state, bufferSource, submitNodeCollector,
                featureRenderDispatcher, List.of()), fogBuffer);
    }

    @WrapOperation(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"
            )
    )
    private RenderTarget enhancedstorage$layerTarget(Minecraft minecraft, Operation<RenderTarget> original) {
        RenderTarget layer = PageLayerCache.targetFor((GuiRenderer) (Object) this);
        return layer != null ? layer : original.call(minecraft);
    }
    //?} else {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void enhancedstorage$renderPageLayers(CallbackInfo ci) {
        if (PageLayerCache.isLayerRenderer((GuiRenderer) (Object) this)) return;
        PageLayerCache.renderPending(state -> new GuiRenderer(state, featureRenderDispatcher, List.of()));
    }

    @WrapOperation(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"
            )
    )
    private RenderTarget enhancedstorage$layerTarget(GameRenderer gameRenderer, Operation<RenderTarget> original) {
        RenderTarget layer = PageLayerCache.targetFor((GuiRenderer) (Object) this);
        return layer != null ? layer : original.call(gameRenderer);
    }
    *///?}
}

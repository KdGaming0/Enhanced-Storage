package com.github.kdgaming0.enhancedstorage.gui.layer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

//? if <26.2 {
import com.mojang.blaze3d.buffers.GpuBufferSlice;
//?} else {
/*import com.mojang.blaze3d.GpuFormat;
 *///?}

/**
 * Offscreen GUI layer for overlay content that rarely changes between frames.
 * <p>
 * Vanilla rebuilds the whole GUI every frame: every slot, item model, label and sprite of every page is extracted,
 * layered, turned into vertices and drawn again, although a cached page's pixels almost never change. A layer owner
 * (a {@link com.github.kdgaming0.enhancedstorage.gui.layout.PageViewport}, or the
 * {@link com.github.kdgaming0.enhancedstorage.gui.component.common.CachedPanelComponent} of the Storage Overview)
 * instead extracts its static content into this class's own {@link GuiRenderState} only when something it shows has
 * changed (see {@link LayerRefresh}), and every frame draws the result as one textured quad.
 * <p>
 * Rendering uses a second vanilla {@link GuiRenderer}, so the output is identical to drawing the same elements
 * directly. It runs right before the main GUI renderer (see {@code GuiRendererMixin}), into a window-sized target
 * that {@code GuiRendererMixin} substitutes for the main render target. Owners occupy disjoint screen areas, so each
 * one only clears and redraws its own rectangle of the shared target.
 */
public final class PageLayerCache {

    /** Mouse position layer content is extracted with, so nothing in it draws as hovered. */
    public static final int OFFSCREEN_MOUSE = -9999;
    private static final long RELEASE_AFTER_IDLE_MS = 10_000;

    private static final GuiRenderState STATE = new GuiRenderState();
    private static final List<ScreenRectangle> DIRTY = new ArrayList<>();
    private static @Nullable GuiRenderer renderer;
    private static @Nullable RenderTarget target;
    private static int generation;
    // The main GUI extractor of the frame the pending layers were extracted in. Vanilla makes a new one per frame.
    private static @Nullable GuiGraphicsExtractor pendingExtractor;
    private static int animatedItems;
    private static boolean rendering;
    private static long lastUsed;

    private PageLayerCache() {
    }

    /**
     * Prepares the shared target for this frame and returns the layer generation. It changes whenever previously
     * rendered layer pixels may have been lost (target created or resized, or an extraction that never got rendered),
     * and an owner must then redraw its layer. Every owner calls this before deciding whether it is up to date.
     */
    public static int sync(GuiGraphicsExtractor main) {
        lastUsed = Util.getMillis();
        ensureTarget();
        if (pendingExtractor != null && pendingExtractor != main) {
            // Extracted on an earlier frame but never rendered (that frame had no GUI render): those layers are missing.
            discardPending();
            generation++;
        }
        return generation;
    }

    /** True while the layer's own renderer is running, so per-frame hooks can tell it apart from the main GUI. */
    public static boolean isRendering() {
        return rendering;
    }

    /**
     * Starts redrawing an owner's layer: returns an extractor that writes into the offscreen state, with the
     * caller's pose and a scissor to the owner's rectangle applied and the mouse at {@link #OFFSCREEN_MOUSE}. Finish it with
     * {@link #endLayer}. Requires {@link #sync} earlier in the same frame.
     */
    public static GuiGraphicsExtractor beginLayer(GuiGraphicsExtractor main, int x, int y, int width, int height) {
        pendingExtractor = main;
        GuiGraphicsExtractor layer = new GuiGraphicsExtractor(Minecraft.getInstance(), STATE, OFFSCREEN_MOUSE, OFFSCREEN_MOUSE);
        layer.pose().set(main.pose());
        layer.enableScissor(x, y, x + width, y + height);
        // The scissor stack holds the area already transformed by the pose, i.e. where the pixels land.
        DIRTY.add(layer.scissorStack.peek());
        return layer;
    }

    /** Ends a layer begun by {@link #beginLayer}; true if it drew animated items. */
    public static boolean endLayer(GuiGraphicsExtractor layer) {
        while (layer.scissorStack.peek() != null) layer.disableScissor();
        int before = animatedItems;
        int[] count = {0};
        STATE.forEachItem(item -> {
            if (item.itemStackRenderState().isAnimated()) count[0]++;
        });
        animatedItems = count[0];
        return animatedItems > before;
    }

    /** Draws the layer's pixels for the given screen rectangle into the main GUI, clipped to the current scissor. */
    public static void blit(GuiGraphicsExtractor main, int x0, int y0, int x1, int y1) {
        RenderTarget layer = target;
        if (layer == null) return;
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        // The layer was drawn with the same pose, so sample where the pose put these corners.
        Vector2f from = main.pose().transformPosition(x0, y0, new Vector2f());
        Vector2f to = main.pose().transformPosition(x1, y1, new Vector2f());
        float u0 = from.x * scale / layer.width;
        float u1 = to.x * scale / layer.width;
        // Render targets are stored bottom-up.
        float v0 = 1.0F - from.y * scale / layer.height;
        float v1 = 1.0F - to.y * scale / layer.height;
        main.guiRenderState.addGuiElement(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(layer.getColorTextureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                new Matrix3x2f(main.pose()),
                x0, y0, x1, y1,
                u0, u1, v0, v1,
                -1,
                main.scissorStack.peek()));
    }

    /** The render target to draw into instead of the main one, when {@code guiRenderer} is the layer renderer. */
    public static @Nullable RenderTarget targetFor(GuiRenderer guiRenderer) {
        return guiRenderer == renderer ? target : null;
    }

    public static boolean isLayerRenderer(GuiRenderer guiRenderer) {
        return guiRenderer == renderer;
    }

    /**
     * Renders the layers extracted this frame. Called right before the main GUI renderer, after the world, so the
     * render state left behind (projection, lighting) is the one the main GUI sets up for itself anyway.
     */
    //? if <26.2 {
    public static void renderPending(Function<GuiRenderState, GuiRenderer> factory, GpuBufferSlice fogBuffer) {
    //?} else {
    /*public static void renderPending(Function<GuiRenderState, GuiRenderer> factory) {
    *///?}
        if (pendingExtractor == null) {
            if ((renderer != null || target != null) && Util.getMillis() - lastUsed > RELEASE_AFTER_IDLE_MS) release();
            return;
        }
        RenderTarget layer = target;
        if (layer == null) {
            discardPending();
            return;
        }
        if (renderer == null) renderer = factory.apply(STATE);

        clearDirty(layer);
        pendingExtractor = null;
        animatedItems = 0;
        rendering = true;
        try {
            //? if <26.2 {
            renderer.render(fogBuffer);
            //?} else {
            /*renderer.render();
             *///?}
            renderer.endFrame();
        } finally {
            rendering = false;
        }
        //? if <26.2 {
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        //?} else {
        /*Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
         *///?}
    }

    private static void clearDirty(RenderTarget layer) {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        for (ScreenRectangle rect : DIRTY) {
            int left = Math.clamp((long) rect.left() * scale, 0, layer.width);
            int right = Math.clamp((long) rect.right() * scale, 0, layer.width);
            int top = Math.clamp((long) rect.top() * scale, 0, layer.height);
            int bottom = Math.clamp((long) rect.bottom() * scale, 0, layer.height);
            if (right <= left || bottom <= top) continue;
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    layer.getColorTexture(), GuiRenderer.CLEAR_COLOR, layer.getDepthTexture(), clearDepth(),
                    left, layer.height - bottom, right - left, bottom - top);
        }
        DIRTY.clear();
    }

    // The value GameRenderer clears the depth buffer to before drawing the GUI (26.2 switched to reverse Z).
    private static double clearDepth() {
        //? if <26.2 {
        return 1.0;
        //?} else {
        /*return 0.0;
         *///?}
    }

    // Sized from the window, not the main render target: during extraction the main target still has last frame's
    // size, and GameRenderer.render only resizes it to the window afterwards, before the layers are rendered.
    private static void ensureTarget() {
        Window window = Minecraft.getInstance().getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        if (width <= 0 || height <= 0) return;
        if (target == null) {
            //? if <26.2 {
            target = new TextureTarget("Enhanced Storage page layer", width, height, true);
            //?} else {
            /*target = new TextureTarget("Enhanced Storage page layer", width, height, true, GpuFormat.RGBA8_UNORM);
             *///?}
            generation++;
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
            generation++;
        }
    }

    private static void discardPending() {
        STATE.reset();
        DIRTY.clear();
        pendingExtractor = null;
        animatedItems = 0;
    }

    private static void release() {
        if (renderer != null) renderer.close();
        if (target != null) target.destroyBuffers();
        renderer = null;
        target = null;
        discardPending();
        generation++;
    }
}

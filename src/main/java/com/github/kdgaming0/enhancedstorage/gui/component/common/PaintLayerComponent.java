package com.github.kdgaming0.enhancedstorage.gui.component.common;

import com.daqem.uilib.gui.component.AbstractComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.NotNull;

/**
 * Full-screen component that paints on top of everything added before it, such as drag visuals.
 */
public class PaintLayerComponent extends AbstractComponent {

    private final Painter painter;

    public PaintLayerComponent(int width, int height, Painter painter) {
        super(0, 0, width, height);
        this.painter = painter;
    }

    // Overridden directly so the painter always gets raw screen coordinates.
    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                       float partialTick, int parentWidth, int parentHeight) {
        painter.paint(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                   float partialTick, int parentWidth, int parentHeight) {
    }

    @FunctionalInterface
    public interface Painter {
        void paint(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY);
    }
}
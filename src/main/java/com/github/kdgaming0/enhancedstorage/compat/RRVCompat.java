package com.github.kdgaming0.enhancedstorage.compat;

import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class RRVCompat {
    private static boolean renderingEarly = false;
    private static boolean renderingHighlightLate = false;

    public static boolean isRenderingEarly() {
        return renderingEarly;
    }

    public static boolean isRenderingHighlightLate() {
        return renderingHighlightLate;
    }


    public static void renderOverlayBelow(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderingEarly = true;
        try {
            OverlayManager.INSTANCE.renderAll(guiGraphics, mouseX, mouseY, partialTicks);
        } finally {
            renderingEarly = false;
        }
    }

    public static void renderHighlightAbove(AbstractContainerScreen<?> screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderingHighlightLate = true;
        try {
            ItemViewOverlay.INSTANCE.renderItemHighlighting(screen, guiGraphics, mouseX, mouseY, partialTicks);
        } finally {
            renderingHighlightLate = false;
        }
    }
}
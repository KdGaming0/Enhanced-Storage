package com.github.kdgaming0.enhancedstorage.gui.component;

import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.NotNull;

public class RowBreakComponent extends GridCellComponent {

    private final LayoutEntry.RowBreak entry;

    public RowBreakComponent(LayoutEntry.RowBreak entry) {
        super(0, 0, 0, 0);
        this.entry = entry;
    }

    @Override
    public LayoutEntry entry() {
        return entry;
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                   float partialTick, int parentWidth, int parentHeight) {
    }
}
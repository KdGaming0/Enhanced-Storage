package com.github.kdgaming0.enhancedstorage.gui.component.section;

import com.github.kdgaming0.enhancedstorage.gui.component.page.GridCellComponent;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.NotNull;

/**
 * Links the page after it under the page before it. Zero-sized; the layout reads it to build stacks.
 */
public class StackJoinComponent extends GridCellComponent {

    private final LayoutEntry.StackJoin entry;

    public StackJoinComponent(LayoutEntry.StackJoin entry) {
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
package com.github.kdgaming0.enhancedstorage.gui.component.section;

import com.github.kdgaming0.enhancedstorage.gui.component.page.GridCellComponent;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.NotNull;

/**
 * Closes a section; entries after it are ungrouped until the next header. Draws nothing and never takes a row.
 */
public class SectionEndComponent extends GridCellComponent {

    private static final LayoutEntry.SectionEnd ENTRY = new LayoutEntry.SectionEnd();

    public SectionEndComponent(int width, int height) {
        super(0, 0, width, height);
    }

    @Override
    public LayoutEntry entry() {
        return ENTRY;
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                   float partialTick, int parentWidth, int parentHeight) {
    }
}
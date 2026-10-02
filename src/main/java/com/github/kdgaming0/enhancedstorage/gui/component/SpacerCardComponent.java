package com.github.kdgaming0.enhancedstorage.gui.component;

import com.github.kdgaming0.enhancedstorage.gui.Rect;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.NotNull;

/**
 * An empty grid cell. It draws nothing, so it is invisible outside edit mode;
 * edit mode paints its outline from the screen.
 */
public class SpacerCardComponent extends GridCellComponent {

    private final LayoutEntry.Spacer entry;
    private int slotHeight;

    public SpacerCardComponent(int width, int height, LayoutEntry.Spacer entry) {
        super(0, 0, width, height);
        this.entry = entry;
        this.slotHeight = height;
    }

    @Override
    public LayoutEntry entry() {
        return entry;
    }

    /**
     * Spacers don't set the row height; they fill whatever height the row's pages need.
     */
    public void setSlotHeight(int slotHeight) {
        this.slotHeight = slotHeight;
    }

    @Override
    public Rect bounds() {
        return new Rect(getTotalX(), getTotalY(), getWidth(), slotHeight);
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                   float partialTick, int parentWidth, int parentHeight) {
    }
}
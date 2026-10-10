package com.github.kdgaming0.enhancedstorage.gui.layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * A {@link PageViewport} cell where hovering changes only part of what it draws, so the viewport only needs to
 * draw that part live instead of the whole cell.
 */
public interface PartialHoverCell {

    /**
     * The screen area that looks different with the mouse at this point, or null if nothing does. The cell is drawn
     * clipped to it, so it must hold everything hovering changes.
     */
    @Nullable Rect hoverArea(int mouseX, int mouseY);

    /**
     * Hover feedback that isn't drawn, such as the cursor. Called every frame the cell is hovered.
     */
    void requestHoverCursor(GuiGraphicsExtractor graphics);
}

package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Scrollable viewport holding the page-card rows. Rows are stacked by {@link #positionRows()}
 * instead of by the container so their positions are valid before the first frame is drawn.
 */
public class PageViewport extends ScrollContainerWidget {

    private final StorageOverlayState state;

    public PageViewport(int width, int height, int rowSpacing, StorageOverlayState state) {
        super(width, height, rowSpacing);
        this.state = state;
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        // Only claim the scrollbar in automatic (getChildAt) dispatch. Everything else falls through so container slot logic can run.
        return this.visible && this.isOverScrollbar(x, y);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (updateScrolling(event)) return true;
        if (event.x() < getX() || event.x() >= getX() + getWidth()
                || event.y() < getY() || event.y() >= getY() + getHeight()) {
            return false;
        }
        for (GuiEventListener child : children()) {
            if (child.mouseClicked(event, doubleClick)) {
                setFocused(child);
                return true;
            }
        }
        return false;
    }

    @Override
    public void setScrollAmount(double amount) {
        super.setScrollAmount(amount);
        state.setScrollAmount(scrollAmount());
        positionRows();
    }

    @Override
    protected double scrollRate() {
        return EnhancedStorageConfig.overlayScrollSpeed;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.enableScissor(this.getX(), this.getY(),
                this.getX() + this.getWidth(), this.getY() + this.getHeight());

        positionRows();
        for (IComponent row : getComponents()) {
            int rowY = row.getY();
            if (rowY + row.getHeight() > 0 && rowY < this.getHeight()) {
                row.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, this.getWidth(), this.getHeight());
            }
        }

        guiGraphics.disableScissor();
        this.extractScrollbar(guiGraphics, mouseX, mouseY);
    }

    /**
     * Stacks the rows vertically at the current scroll offset. Runs eagerly (on reflow and scroll), not
     * only while rendering, because the live page's container slots are placed from the open card's
     * position before the first frame is drawn. Rows left at y=0 until then flash its items in row one.
     */
    void positionRows() {
        List<IComponent> rows = getComponents();
        int currentY = -(int) scrollAmount();
        for (int i = 0; i < rows.size(); i++) {
            IComponent row = rows.get(i);
            // UI Lib propagates setY through the row's entire component tree, so skip it when unchanged.
            if (row.getY() != currentY) {
                row.setY(currentY);
            }
            currentY += row.getHeight();
            if (i < rows.size() - 1) {
                currentY += getContentSpacing();
            }
        }
    }
}
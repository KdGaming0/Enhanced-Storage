package com.github.kdgaming0.enhancedstorage.gui.layout;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.component.EmptyComponent;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.layer.LayerItems;
import com.github.kdgaming0.enhancedstorage.gui.layer.LayerRefresh;
import com.github.kdgaming0.enhancedstorage.gui.layer.PageLayerCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

/**
 * Scrollable viewport holding the page-card rows. Rows are stacked by {@link #positionRows()}
 * instead of by the container so their positions are valid before the first frame is drawn.
 * <p>
 * With the page layer enabled, cells are drawn once into a {@link PageLayerCache} layer and reused. Only
 * {@link LiveCell}s that ask for it (the open page, a title being renamed) are left out and extracted every frame.
 * The cell under the mouse is extracted every frame too, into a gap left in the layer's blit, so hovering never
 * redraws the layer; a cell must therefore not draw outside its own bounds. For a {@link PartialHoverCell} the gap
 * is only the part that hovering changes, such as one slot of a page card. Animated items are left out of the layer
 * and drawn every frame on top of it (see {@link LayerItems}).
 */
public class PageViewport extends ScrollContainerWidget {

    private final DoubleConsumer scrollListener;
    private final LayerRefresh layerRefresh = new LayerRefresh();
    private @Nullable Consumer<GuiGraphicsExtractor> backgroundPainter;
    private @Nullable BooleanSupplier layerEnabled;
    private Supplier<?> layerInputs = () -> null;
    // The animated items left out of the current layer.
    private LayerItems layerItems = LayerItems.NONE;

    public PageViewport(int width, int height, int rowSpacing, DoubleConsumer scrollListener) {
        super(width, height, rowSpacing);
        this.scrollListener = scrollListener;
    }

    /**
     * The part of the hovered cell that hovering changes: the whole cell, or a {@link PartialHoverCell}'s area.
     */
    private static @Nullable Rect hoverGap(GuiGraphicsExtractor guiGraphics, IComponent hovered, int mouseX, int mouseY) {
        if (hovered instanceof PartialHoverCell partial) {
            partial.requestHoverCursor(guiGraphics);
            return partial.hoverArea(mouseX, mouseY);
        }
        return new Rect(hovered.getTotalX(), hovered.getTotalY(), hovered.getWidth(), hovered.getHeight());
    }

    private static boolean isLive(IComponent cell) {
        return cell instanceof LiveCell liveCell && liveCell.drawsLive();
    }

    private static boolean contains(IComponent cell, int mouseX, int mouseY) {
        int x = cell.getTotalX();
        int y = cell.getTotalY();
        return mouseX >= x && mouseX < x + cell.getWidth() && mouseY >= y && mouseY < y + cell.getHeight();
    }

    /**
     * Paints behind the rows, inside the viewport's scissor, after the rows are positioned for this frame.
     */
    public void setBackgroundPainter(@Nullable Consumer<GuiGraphicsExtractor> painter) {
        this.backgroundPainter = painter;
    }

    /**
     * Draws unchanging cells from a cached layer while {@code enabled} holds. {@code inputs} returns the outside
     * state the cells read while drawing (compared with {@link java.util.Objects#equals}); the layer is redrawn when it changes.
     */
    public void setPageLayer(BooleanSupplier enabled, Supplier<?> inputs) {
        this.layerEnabled = enabled;
        this.layerInputs = inputs;
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
        scrollListener.accept(scrollAmount());
        positionRows();
    }

    @Override
    protected double scrollRate() {
        return EnhancedStorageConfig.overlayScrollSpeed;
    }

    @Override
    protected int contentHeight() {
        List<IComponent> rows = getComponents();
        if (rows.isEmpty()) return 0;
        int height = getContentSpacing() * (rows.size() - 1);
        for (IComponent row : rows) height += row.getHeight();
        return height;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.enableScissor(this.getX(), this.getY(),
                this.getX() + this.getWidth(), this.getY() + this.getHeight());

        positionRows();
        if (layerEnabled != null && layerEnabled.getAsBoolean()) {
            extractLayered(guiGraphics, mouseX, mouseY, partialTick);
        } else {
            extractDirect(guiGraphics, mouseX, mouseY, partialTick);
        }

        guiGraphics.disableScissor();
        this.extractScrollbar(guiGraphics, mouseX, mouseY);
    }

    private void extractDirect(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (backgroundPainter != null) backgroundPainter.accept(guiGraphics);
        // Vanilla layers each new element by searching every earlier element in the current stratum, which is
        // quadratic in the element count. Starting a fresh stratum keeps the rows out of the search for whatever
        // was drawn before them (and page cards start their own). Later strata draw on top, matching draw order.
        guiGraphics.nextStratum();
        for (IComponent row : getComponents()) {
            if (isRowVisible(row)) {
                row.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, this.getWidth(), this.getHeight());
            }
        }
        guiGraphics.nextStratum();
    }

    private void extractLayered(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        List<IComponent> live = collectLiveCells();
        PageLayerCache pages = PageLayerCache.PAGES;
        LayerKey key = new LayerKey(pages.sync(guiGraphics), List.copyOf(getComponents()), (int) scrollAmount(),
                getX(), getY(), getWidth(), getHeight(), new Matrix3x2f(guiGraphics.pose()), live, layerInputs.get());
        if (layerRefresh.needsRedraw(key)) {
            GuiGraphicsExtractor layer = pages.beginLayer(guiGraphics, getX(), getY(), getWidth(), getHeight());
            if (backgroundPainter != null) backgroundPainter.accept(layer);
            layer.nextStratum();
            int noMouse = PageLayerCache.OFFSCREEN_MOUSE;
            layerItems = LayerItems.collect(null,
                    () -> extractCells(layer, noMouse, noMouse, partialTick, cell -> !live.contains(cell), true));
            layerRefresh.drawn(key, pages.endLayer(layer));
        }

        guiGraphics.nextStratum();
        IComponent hovered = hoveredCell(mouseX, mouseY, live);
        Rect gap = hovered == null ? null : hoverGap(guiGraphics, hovered, mouseX, mouseY);
        int right = getX() + getWidth();
        int bottom = getY() + getHeight();
        pages.blitAround(guiGraphics, getX(), getY(), right, bottom, gap);
        if (gap != null) extractHovered(guiGraphics, hovered, gap, mouseX, mouseY, partialTick);
        layerItems.extract(guiGraphics, getX(), getY(), right, bottom, gap);
        extractCells(guiGraphics, mouseX, mouseY, partialTick, live::contains, false);
        guiGraphics.nextStratum();
    }

    /**
     * Draws the background and the hovered cell itself live in the gap, clipped to it. The layers leave the gap out
     * (their copy is drawn without hover), so translucent cells aren't drawn twice. Animated items aren't either: see
     * {@link LayerItems#extract}.
     */
    private void extractHovered(GuiGraphicsExtractor guiGraphics, IComponent hovered, Rect area, int mouseX, int mouseY,
                                float partialTick) {
        int left = getX();
        int top = getY();
        int right = left + getWidth();
        int bottom = top + getHeight();
        guiGraphics.enableScissor(Math.clamp(area.x(), left, right), Math.clamp(area.y(), top, bottom),
                Math.clamp(area.right(), left, right), Math.clamp(area.bottom(), top, bottom));
        if (backgroundPainter != null) backgroundPainter.accept(guiGraphics);
        guiGraphics.nextStratum();
        Runnable cell = () -> extractCells(guiGraphics, mouseX, mouseY, partialTick, c -> c == hovered, false);
        if (LayerItems.drawnLive()) {
            // Result unused: items only partly in the gap are drawn by layerItems.extract, which skips the rest.
            LayerItems.collect(area, cell);
        } else {
            cell.run();
        }
        guiGraphics.disableScissor();
    }

    private boolean isRowVisible(IComponent row) {
        int rowY = row.getY();
        return rowY + row.getHeight() > 0 && rowY < this.getHeight();
    }

    /**
     * The visible cells: the cells of grouping rows (plain {@link EmptyComponent}s), or the row itself otherwise.
     */
    private List<IComponent> visibleCells() {
        List<IComponent> cells = new ArrayList<>();
        for (IComponent row : getComponents()) {
            if (!isRowVisible(row)) continue;
            if (row instanceof EmptyComponent container) cells.addAll(container.getComponents());
            else cells.add(row);
        }
        return cells;
    }

    /**
     * Cells drawn every frame instead of from the layer: {@link LiveCell}s that ask to be.
     */
    private List<IComponent> collectLiveCells() {
        List<IComponent> live = new ArrayList<>(2);
        for (IComponent cell : visibleCells()) {
            if (isLive(cell)) live.add(cell);
        }
        return live;
    }

    /**
     * The visible cell under the mouse, unless it is drawn live anyway.
     */
    private @Nullable IComponent hoveredCell(int mouseX, int mouseY, List<IComponent> live) {
        // A clipped-off part of a cell can't be hovered: hover and tooltip checks require the point inside the scissor.
        if (mouseX < getX() || mouseX >= getX() + getWidth() || mouseY < getY() || mouseY >= getY() + getHeight()) {
            return null;
        }
        for (IComponent cell : visibleCells()) {
            if (contains(cell, mouseX, mouseY)) return live.contains(cell) ? null : cell;
        }
        return null;
    }

    /**
     * Extracts the visible rows' cells accepted by {@code include}, in draw order. {@code withRows} also draws the
     * grouping rows themselves. Mirrors UI Lib's {@code AbstractComponent.extractRenderStateBase} for grouping rows.
     */
    private void extractCells(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                              Predicate<IComponent> include, boolean withRows) {
        for (IComponent row : getComponents()) {
            if (!isRowVisible(row)) continue;
            if (row instanceof EmptyComponent container) {
                for (IComponent cell : container.getComponents()) {
                    if (cell.isRenderBeforeParent() && include.test(cell)) {
                        cell.extractRenderStateBase(graphics, mouseX, mouseY, partialTick, container.getWidth(), container.getHeight());
                    }
                }
                if (withRows) {
                    container.extractRenderState(graphics, mouseX, mouseY, partialTick, this.getWidth(), this.getHeight());
                }
                for (IComponent cell : container.getComponents()) {
                    if (!cell.isRenderBeforeParent() && include.test(cell)) {
                        cell.extractRenderStateBase(graphics, mouseX, mouseY, partialTick, container.getWidth(), container.getHeight());
                    }
                }
                if (withRows) {
                    for (IWidget widget : container.getWidgets()) {
                        widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
                    }
                }
            } else if (include.test(row)) {
                row.extractRenderStateBase(graphics, mouseX, mouseY, partialTick, this.getWidth(), this.getHeight());
            }
        }
    }

    /**
     * Stacks the rows vertically at the current scroll offset. Runs eagerly (on reflow and scroll), not
     * only while rendering, because the live page's container slots are placed from the open card's
     * position before the first frame is drawn. Rows left at y=0 until then flash its items in row one.
     */
    public void positionRows() {
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

    // Everything the layer shows. The pose is part of it because the layer's pixels sit where the pose put them when it was drawn.
    private record LayerKey(int generation, List<IComponent> rows, int scroll, int x, int y, int width, int height,
                            Matrix3x2f pose, List<IComponent> liveCells, @Nullable Object inputs) {
    }
}

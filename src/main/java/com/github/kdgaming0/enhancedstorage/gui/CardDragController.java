package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.gui.component.text.TextComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.*;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Drag-and-drop reordering of page cards in edit mode.
 */
final class CardDragController {

    private static final float GHOST_ALPHA = 0.7f;
    private static final int GHOST_TITLE_OFFSET = 3;
    private static final int SCROLL_EDGE = 24;
    private static final double MAX_SCROLL_SPEED = 400; // pixels per second
    private static final long SPRING_DELAY_MS = 600;
    private static final int STACK_BAND = 12;

    private final StorageOverlayLayout layout;
    private final Consumer<List<LayoutEntry>> onDrop;

    private @Nullable GridCellComponent dragged;
    private List<GridCellComponent> originalOrder = List.of();
    private @Nullable TextComponent ghostTitle;
    private double grabOffsetX;
    private double grabOffsetY;
    private int ghostTitleX;
    private int ghostTitleY;
    private int screenWidth;
    private int screenHeight;
    private long lastFrameMs;
    private @Nullable SectionHeaderComponent springTarget;
    private long springStartMs;
    private final List<SectionHeaderComponent> springOpened = new ArrayList<>();
    private @Nullable List<GridCellComponent> undoOrder;
    private @Nullable Rect moveZone;
    private @Nullable GridCellComponent dragJoin;

    CardDragController(StorageOverlayLayout layout, Consumer<List<LayoutEntry>> onDrop) {
        this.layout = layout;
        this.onDrop = onDrop;
    }

    boolean isActive() {
        return dragged != null;
    }

    void start(GridCellComponent cell, double pressX, double pressY, int screenWidth, int screenHeight) {
        this.dragged = cell;
        this.originalOrder = List.copyOf(layout.getCells());
        this.grabOffsetX = pressX - cell.getTotalX();
        this.grabOffsetY = pressY - cell.getTotalY();
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.lastFrameMs = Util.getMillis();
        this.springTarget = null;
        this.ghostTitle = null;
        this.ghostTitleX = GHOST_TITLE_OFFSET;
        this.ghostTitleY = GHOST_TITLE_OFFSET;

        String name = null;
        if (cell instanceof PageCardComponent card) {
            name = PageNames.getInstance().get(card.getKey()).orElse(card.getKey().displayName());
        } else if (cell instanceof SectionHeaderComponent header) {
            name = header.entry().name();
            Rect nameBounds = header.nameBounds();
            this.ghostTitleX = nameBounds.x() + 2 - header.getTotalX();
            this.ghostTitleY = nameBounds.y() + 3 - header.getTotalY();
            // Moved as its bar only, so the block stays small and the grid doesn't jump around.
            layout.setDraggedSection(header);
        }
        if (name != null) {
            TextComponent title = new TextComponent(0, 0, Component.literal(name), OverlayTheme.titleColor());
            title.setDrawShadow(OverlayTheme.titleShadow());
            title.updateParentPosition(0, 0, screenWidth, screenHeight);
            this.ghostTitle = title;
        }
        if (cell instanceof PageCardComponent) {
            this.dragJoin = new StackJoinComponent(new LayoutEntry.StackJoin(layout.nextStackJoinId()));
            List<GridCellComponent> detached = detach(layout.getCells(), cell);
            if (detached != null) layout.previewOrder(detached);
        }
    }

    void update(double mouseX, double mouseY) {
        GridCellComponent cell = dragged;
        Rect viewport = layout.getPageOverviewBounds();
        if (cell == null || viewport == null) return;
        if (cell instanceof SectionHeaderComponent header) {
            updateSection(header, mouseX, mouseY);
            return;
        }

        // The full order, including cells hidden in collapsed sections, so a save never drops them.
        List<GridCellComponent> current = layout.getCells();
        if (!current.contains(cell)) return;

        GridCellComponent hovered = cellUnder(layout.getVisibleCells(), viewport, mouseX, mouseY);
        SectionHeaderComponent spring = hovered instanceof SectionHeaderComponent h && h.isCollapsed() ? h : null;
        if (spring != springTarget) {
            springTarget = spring;
            springStartMs = Util.getMillis();
        }

        // Positions are picked in the order without the dragged card and its join, then the card goes back in there.
        List<GridCellComponent> order = new ArrayList<>(current);
        order.remove(cell);
        if (dragJoin != null) order.remove(dragJoin);

        if (hovered instanceof SectionHeaderComponent header) {
            // First in that section. A collapsed one opens after a short hover instead (see paint).
            if (header.isCollapsed()) return;
            order.add(order.indexOf(header) + 1, cell);
        } else if (hovered != null) {
            if (hovered == cell) return;
            Rect bounds = hovered.bounds();
            int at = order.indexOf(hovered);
            int band = stackBand(cell, hovered);
            if (mouseY < bounds.y() + band) {
                order.add(at, dragJoin);
                order.add(at, cell);
            } else if (mouseY >= bounds.bottom() - band) {
                order.add(at + 1, cell);
                order.add(at + 1, dragJoin);
            } else {
                boolean after = mouseX >= bounds.x() + bounds.width() / 2.0;
                order.add(after ? StorageOverlayLayout.stackEnd(order, at) : StorageOverlayLayout.stackStart(order, at), cell);
            }
        } else {
            int insertBefore = insertionInEmptySpace(current, mouseX, mouseY);
            // Other gaps keep the current slot, so the grid doesn't jitter.
            if (insertBefore < 0) return;
            order.add(withoutDragged(current, insertBefore), cell);
        }
        if (order.equals(current)) return;

        // A cell moved into a collapsed section would vanish mid-drag.
        SectionHeaderComponent section = sectionOf(order, order.indexOf(cell));
        if (section != null && section.isCollapsed()) return;

        if (moveZone != null && !moveZone.contains(mouseX, mouseY)) {
            undoOrder = null;
            moveZone = null;
        }

        if (order.equals(undoOrder)) return;
        boolean byCard = hovered != null && !(hovered instanceof SectionHeaderComponent);
        undoOrder = byCard ? List.copyOf(current) : null;
        moveZone = byCard ? hovered.bounds() : null;
        layout.previewOrder(order);
    }

    private static int stackBand(GridCellComponent dragged, GridCellComponent target) {
        if (!(dragged instanceof PageCardComponent) || !(target instanceof PageCardComponent)) return 0;
        return Math.min(STACK_BAND, target.getHeight() / 4);
    }

    private int withoutDragged(List<GridCellComponent> current, int insertBefore) {
        int index = 0;
        for (int i = 0; i < insertBefore && i < current.size(); i++) {
            GridCellComponent c = current.get(i);
            if (c != dragged && c != dragJoin) index++;
        }
        return index;
    }

    /**
     * The order with the card taken out of its stack and placed beside it, or null if it isn't stacked.
     * In the middle of a stack, the pages above and below stay linked to each other.
     */
    private static @Nullable List<GridCellComponent> detach(List<GridCellComponent> order, GridCellComponent card) {
        int i = order.indexOf(card);
        boolean above = StorageOverlayLayout.isLink(order, i - 1);
        boolean below = StorageOverlayLayout.isLink(order, i + 1);
        if (!above && !below) return null;

        GridCellComponent neighbour = order.get(above ? i - 2 : i + 2);
        List<GridCellComponent> result = new ArrayList<>(order);
        result.remove(below ? i + 1 : i - 1);
        result.remove(card);
        int n = result.indexOf(neighbour);
        result.add(above ? StorageOverlayLayout.stackEnd(result, n) : StorageOverlayLayout.stackStart(result, n), card);
        return result;
    }

    private int insertionInEmptySpace(List<GridCellComponent> order, double mouseX, double mouseY) {
        StorageOverlayLayout.SectionFrame frame = layout.sectionFrameAt(mouseX, mouseY);
        if (frame != null) {
            // Beside or below the section's last row: last in that section.
            if (mouseY < frame.lastRow().getTotalY()) return -1;
            return nextBoundary(order, order.indexOf(frame.header()) + 1);
        }
        StorageOverlayLayout.SectionSlot slot = layout.boundaryAt(mouseX, mouseY, true);
        if (slot != null) return slot.index();
        return layout.isPastLastCell(mouseX, mouseY) ? order.size() : -1;
    }

    private void updateSection(SectionHeaderComponent header, double mouseX, double mouseY) {
        List<GridCellComponent> order = new ArrayList<>(layout.getCells());
        int start = order.indexOf(header);
        if (start < 0) return;
        List<GridCellComponent> span = order.subList(start, afterSection(order, start));
        List<GridCellComponent> block = new ArrayList<>(span);
        span.clear();

        int to = -1;
        StorageOverlayLayout.SectionFrame frame = layout.sectionFrameAt(mouseX, mouseY);
        if (frame != null) {
            if (frame.header() == header) return;
            Rect bounds = frame.bounds();
            int at = order.indexOf(frame.header());
            to = mouseY < bounds.y() + bounds.height() / 2.0 ? at : afterSection(order, at);
        } else {
            StorageOverlayLayout.PlacedRow row = layout.ungroupedRowAt(mouseX, mouseY);
            if (row != null) {
                Rect bounds = row.bounds();
                to = mouseY < bounds.y() + bounds.height() / 2.0
                        ? order.indexOf(row.first()) : order.indexOf(row.last()) + 1;
            } else {
                StorageOverlayLayout.SectionSlot slot = layout.boundaryAt(mouseX, mouseY, true);
                if (slot != null) {
                    List<GridCellComponent> full = layout.getCells();
                    // The slot points into the full order; -1 if it points into the dragged section itself.
                    to = slot.index() >= full.size() ? order.size() : order.indexOf(full.get(slot.index()));
                }
            }
        }
        if (to < 0) return;

        order.addAll(to, block);
        if (!order.equals(layout.getCells())) layout.previewOrder(order);
    }

    /**
     * The index just past the section starting at {@code headerIndex}, including its end marker.
     */
    private static int afterSection(List<GridCellComponent> order, int headerIndex) {
        int end = nextBoundary(order, headerIndex + 1);
        return end < order.size() && order.get(end) instanceof SectionEndComponent ? end + 1 : end;
    }

    private static int nextBoundary(List<GridCellComponent> order, int from) {
        for (int i = from; i < order.size(); i++) {
            if (order.get(i) instanceof SectionHeaderComponent || order.get(i) instanceof SectionEndComponent) return i;
        }
        return order.size();
    }

    private static @Nullable SectionHeaderComponent sectionOf(List<GridCellComponent> order, int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (order.get(i) instanceof SectionEndComponent) return null;
            if (order.get(i) instanceof SectionHeaderComponent header) return header;
        }
        return null;
    }

    void cancel() {
        if (dragged != null && !layout.getCells().equals(originalOrder)) {
            layout.previewOrder(originalOrder);
        }
        reset();
    }

    void reset() {
        boolean wasSection = dragged instanceof SectionHeaderComponent;
        boolean closeSprung = !springOpened.isEmpty();
        dragged = null;
        ghostTitle = null;
        originalOrder = List.of();
        springTarget = null;
        undoOrder = null;
        moveZone = null;
        dragJoin = null;
        springOpened.forEach(SectionHeaderComponent::closeAfterDrag);
        springOpened.clear();
        if (wasSection) {
            layout.setDraggedSection(null);
        } else if (closeSprung) {
            layout.refresh();
        }
    }

    void drop() {
        GridCellComponent cell = dragged;
        if (cell == null) return;
        List<GridCellComponent> finalOrder = List.copyOf(layout.getCells());
        boolean changed = !finalOrder.equals(originalOrder);
        SectionHeaderComponent landedIn = sectionOf(finalOrder, finalOrder.indexOf(cell));
        SectionHeaderComponent keepOpen = springOpened.contains(landedIn) ? landedIn : null;
        reset();
        if (!changed) return;

        List<LayoutEntry> entries = new ArrayList<>();
        for (GridCellComponent c : finalOrder) {
            entries.add(c == keepOpen ? keepOpen.entry().withCollapsed(false) : c.entry());
        }
        onDrop.accept(entries);
    }

    void paint(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GridCellComponent cell = dragged;
        Rect viewport = layout.getPageOverviewBounds();
        if (cell == null || viewport == null) return;

        SectionHeaderComponent spring = springTarget;
        if (spring != null && Util.getMillis() - springStartMs >= SPRING_DELAY_MS) {
            springTarget = null;
            spring.openForDrag();
            springOpened.add(spring);
            layout.refresh();
            update(mouseX, mouseY);
        }

        if (autoScroll(viewport, mouseY)) {
            // Cards slide under a still cursor while scrolling, so pick the slot again.
            update(mouseX, mouseY);
        }

        // The dragged card already sits in its landing slot; cover it with an empty active card to mark the drop spot.
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        Rect slot = cell.bounds();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.PAGE_CARD_ACTIVE),
                slot.x(), slot.y(), slot.right() - slot.x(), slot.bottom() - slot.y());
        if (springTarget != null) {
            Rect bar = springTarget.bounds();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.PAGE_CARD_HOVER),
                    bar.x(), bar.y(), bar.width(), bar.height());
        }
        graphics.disableScissor();

        int ghostX = (int) (mouseX - grabOffsetX);
        int ghostY = (int) (mouseY - grabOffsetY);
        int ghostColor = ARGB.white(GHOST_ALPHA);
        if (cell instanceof SectionHeaderComponent) {
            // Frame plus the strip inside its 2 px border, as the collapsed bar draws it.
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.SECTION_FRAME),
                    ghostX, ghostY, slot.width(), slot.height(), ghostColor);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.SECTION_HEADER),
                    ghostX + 2, ghostY + 2, slot.width() - 4, slot.height() - 4, ghostColor);
        } else {
            String ghostSprite = cell instanceof SpacerCardComponent ? OverlayTheme.PAGE_SPACER : OverlayTheme.PAGE_CARD_IDLE;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(ghostSprite),
                    ghostX, ghostY, slot.width(), slot.height(), ghostColor);
        }
        if (ghostTitle != null) {
            ghostTitle.setX(ghostX + ghostTitleX);
            ghostTitle.setY(ghostY + ghostTitleY);
            ghostTitle.extractRenderStateBase(graphics, mouseX, mouseY, 0, screenWidth, screenHeight);
        }
    }

    // Scrolls the page overview when the cursor is near the top or bottom edge of the viewport.

    /**
     * Time-based, so speed doesn't depend on frame rate. Returns true if the content moved.
     */
    private boolean autoScroll(Rect viewport, int mouseY) {
        long now = Util.getMillis();
        double seconds = (now - lastFrameMs) / 1000.0;
        lastFrameMs = now;

        double depth;
        if (mouseY < viewport.y() + SCROLL_EDGE) {
            depth = mouseY - (viewport.y() + SCROLL_EDGE);
        } else if (mouseY > viewport.bottom() - SCROLL_EDGE) {
            depth = mouseY - (viewport.bottom() - SCROLL_EDGE);
        } else {
            return false;
        }

        double speed = MAX_SCROLL_SPEED * Math.clamp(depth / SCROLL_EDGE, -1.0, 1.0);
        PageViewport scroller = layout.getPageOverview();
        double before = scroller.scrollAmount();
        scroller.setScrollAmount(before + speed * seconds);
        return scroller.scrollAmount() != before;
    }

    private static @Nullable GridCellComponent cellUnder(List<GridCellComponent> cells, Rect viewport, double x, double y) {
        if (!viewport.contains(x, y)) return null;
        for (GridCellComponent cell : cells) {
            if (cell.bounds().contains(x, y)) return cell;
        }
        return null;
    }
}
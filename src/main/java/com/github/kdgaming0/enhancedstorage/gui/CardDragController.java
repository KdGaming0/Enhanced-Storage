package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.gui.component.text.TextComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.page.GridCellComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.page.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.page.SpacerCardComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.section.SectionEndComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.section.SectionHeaderComponent;
import com.github.kdgaming0.enhancedstorage.gui.layout.PageViewport;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutOps;
import com.github.kdgaming0.enhancedstorage.storage.data.PageWidths;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.util.TextUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Drag-and-drop reordering of page cards, spaces and sections in edit mode.
 *
 * <p>A page or space lands where the cursor points at a card, split into fixed zones: the left or right half
 * puts it in its own column beside that card, a thin edge at the top or bottom stacks it. The result is always worked out from
 * the layout as it was before the drag, so the same cursor position gives the same layout.</p>
 */
final class CardDragController {

    private static final float GHOST_ALPHA = 0.7f;
    private static final int GHOST_TITLE_OFFSET = 3;
    private static final int SCROLL_EDGE = 24;
    private static final double MAX_SCROLL_SPEED = 400; // pixels per second
    private static final long SPRING_DELAY_MS = 600;
    private static final int INDICATOR_COLOR = 0xFFFFD24A;
    private static final int INDICATOR_FILL = 0x40FFD24A;
    private static final int INDICATOR_HINT = 0x18FFD24A;
    private static final int INDICATOR_WIDTH = 2;
    private static final int STACK_BAND = 14;

    private final StorageOverlayLayout layout;
    private final Consumer<List<LayoutEntry>> onDrop;
    private final List<SectionHeaderComponent> springOpened = new ArrayList<>();
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
    private List<LayoutEntry> originalEntries = List.of();
    private @Nullable LayoutEntry item;
    private @Nullable List<LayoutEntry> undoEntries;
    private @Nullable Rect moveZone;

    CardDragController(StorageOverlayLayout layout, Consumer<List<LayoutEntry>> onDrop) {
        this.layout = layout;
        this.onDrop = onDrop;
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

    private static @Nullable GridCellComponent cellUnder(List<GridCellComponent> cells, Rect viewport, double x, double y) {
        if (!viewport.contains(x, y)) return null;
        for (GridCellComponent cell : cells) {
            if (cell.bounds().contains(x, y)) return cell;
        }
        return null;
    }

    boolean isActive() {
        return dragged != null;
    }

    void start(GridCellComponent cell, double pressX, double pressY, int screenWidth, int screenHeight) {
        this.dragged = cell;
        this.originalOrder = List.copyOf(layout.getCells());
        this.originalEntries = layout.currentEntries();
        this.item = cell instanceof PageCardComponent card ? new LayoutEntry.Page(card.getKey())
                : cell instanceof SpacerCardComponent ? cell.entry() : null;
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
            TextComponent title = new TextComponent(0, 0, Component.literal(TextUtils.colorCodes(name)), OverlayTheme.titleColor());
            title.setDrawShadow(OverlayTheme.titleShadow());
            title.updateParentPosition(0, 0, screenWidth, screenHeight);
            this.ghostTitle = title;
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
        LayoutEntry moved = item;
        if (moved == null || !layout.getCells().contains(cell)) return;

        GridCellComponent hovered = cellUnder(layout.getVisibleCells(), viewport, mouseX, mouseY);
        SectionHeaderComponent spring = hovered instanceof SectionHeaderComponent h && h.isCollapsed() ? h : null;
        if (spring != springTarget) {
            springTarget = spring;
            springStartMs = Util.getMillis();
        }

        LayoutOps.Target target;
        if (hovered instanceof SectionHeaderComponent header) {
            // First in that section. A collapsed one opens after a short hover instead (see paint).
            if (header.isCollapsed()) return;
            target = new LayoutOps.Target.After(header.entry());
        } else if (hovered != null && !(hovered instanceof SectionEndComponent)) {
            if (hovered == cell) return;
            target = targetOf(hovered, zoneAt(hovered, cell, mouseX, mouseY));
        } else {
            int insertBefore = insertionInEmptySpace(layout.getCells(), mouseX, mouseY);
            // Other gaps keep the current slot, so the grid doesn't jitter.
            if (insertBefore < 0) return;
            long before = layout.entriesBeforeCell(insertBefore).stream().filter(e -> !e.equals(moved)).count();
            target = new LayoutOps.Target.At((int) before);
        }

        List<LayoutEntry> current = layout.currentEntries();
        List<LayoutEntry> order = LayoutOps.move(originalEntries, moved, target);
        if (order == null || order.equals(current)) return;

        // A cell moved into a collapsed section would vanish mid-drag.
        if (landsInCollapsedSection(order, moved)) return;

        if (moveZone != null && !moveZone.contains(mouseX, mouseY)) {
            undoEntries = null;
            moveZone = null;
        }

        if (order.equals(undoEntries)) return;
        boolean byCard = hovered != null && !(hovered instanceof SectionHeaderComponent);
        undoEntries = byCard ? current : null;
        moveZone = byCard ? hovered.bounds() : null;
        layout.previewEntries(order);
    }

    private boolean landsInCollapsedSection(List<LayoutEntry> order, LayoutEntry moved) {
        int at = LayoutOps.indexOfAnchor(order, moved);
        LayoutEntry.Section section = null;
        for (int i = 0; i < at; i++) {
            if (order.get(i) instanceof LayoutEntry.Section s) section = s;
            else if (order.get(i) instanceof LayoutEntry.SectionEnd) section = null;
        }
        if (section == null) return false;
        for (GridCellComponent c : layout.getCells()) {
            if (c instanceof SectionHeaderComponent header && header.entry().equals(section)) return header.isCollapsed();
        }
        return false;
    }

    private enum Zone {BEFORE, AFTER, ABOVE, BELOW}

    private static Zone zoneAt(GridCellComponent hovered, GridCellComponent dragged, double mouseX, double mouseY) {
        Rect bounds = hovered.bounds();
        if (dragged instanceof PageCardComponent && hovered instanceof PageCardComponent) {
            int band = stackBand(bounds);
            if (mouseY < bounds.y() + band) return Zone.ABOVE;
            if (mouseY >= bounds.bottom() - band) return Zone.BELOW;
        }
        return mouseX < bounds.x() + bounds.width() / 2.0 ? Zone.BEFORE : Zone.AFTER;
    }

    private static int stackBand(Rect bounds) {
        return Math.min(STACK_BAND, bounds.height() / 4);
    }

    private static LayoutOps.Target targetOf(GridCellComponent hovered, Zone zone) {
        return switch (zone) {
            case BEFORE -> new LayoutOps.Target.Before(hovered.entry());
            case AFTER -> new LayoutOps.Target.After(hovered.entry());
            case ABOVE -> new LayoutOps.Target.Stack(((PageCardComponent) hovered).getKey(), true);
            case BELOW -> new LayoutOps.Target.Stack(((PageCardComponent) hovered).getKey(), false);
        };
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

    void cancel() {
        if (dragged != null && !layout.currentEntries().equals(originalEntries)) {
            layout.previewEntries(originalEntries);
        }
        reset();
    }

    void reset() {
        boolean wasSection = dragged instanceof SectionHeaderComponent;
        boolean closeSprung = !springOpened.isEmpty();
        dragged = null;
        item = null;
        ghostTitle = null;
        originalOrder = List.of();
        originalEntries = List.of();
        springTarget = null;
        undoEntries = null;
        moveZone = null;
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
        List<LayoutEntry> finalEntries = layout.currentEntries();
        boolean changed = !finalEntries.equals(originalEntries);
        SectionHeaderComponent landedIn = sectionOf(finalOrder, finalOrder.indexOf(cell));
        SectionHeaderComponent keepOpen = springOpened.contains(landedIn) ? landedIn : null;
        reset();
        if (!changed) return;

        List<LayoutEntry> entries = new ArrayList<>();
        for (LayoutEntry entry : finalEntries) {
            entries.add(keepOpen != null && entry.equals(keepOpen.entry()) ? keepOpen.entry().withCollapsed(false) : entry);
        }
        if (cell instanceof PageCardComponent card) joinStackWidth(entries, card.getKey());
        onDrop.accept(entries);
    }

    /**
     * A page dropped into a stack takes the stack's width, so the whole stack stays one column.
     */
    private static void joinStackWidth(List<LayoutEntry> entries, StorageKey key) {
        int at = LayoutOps.indexOfKey(entries, key);
        if (at < 0 || !(entries.get(at) instanceof LayoutEntry.Stack(List<StorageKey> keys))) return;
        StorageKey mate = keys.stream().filter(k -> !k.equals(key)).findFirst().orElse(null);
        if (mate == null) return;
        Optional<Integer> width = PageWidths.getInstance().get(mate);
        if (width.isPresent()) PageWidths.getInstance().set(key, width.get());
        else PageWidths.getInstance().clear(key);
    }

    // Scrolls the page overview when the cursor is near the top or bottom edge of the viewport.

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
        paintIndicator(graphics, viewport, cell, mouseX, mouseY);
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

    /**
     * Marks what the cursor is pointing at: a line beside the column for reordering, the highlighted top or
     * bottom edge for stacking, and an outline around the stack the dragged page now sits in. Drawn inside the clipped viewport
     * and inside existing bounds, so nothing gets bigger.
     */
    private void paintIndicator(GuiGraphicsExtractor graphics, Rect viewport, GridCellComponent cell,
                                int mouseX, int mouseY) {
        if (cell instanceof SectionHeaderComponent) return;
        List<GridCellComponent> cells = layout.getCells();
        int index = cells.indexOf(cell);
        if (index >= 0 && cell.stackTag() != GridCellComponent.NO_STACK) {
            Rect stack = union(cells, StorageOverlayLayout.stackStart(cells, index), StorageOverlayLayout.stackEnd(cells, index));
            outline(graphics, stack);
        }

        GridCellComponent hovered = cellUnder(layout.getVisibleCells(), viewport, mouseX, mouseY);
        if (hovered == null || hovered == cell || hovered instanceof SectionHeaderComponent
                || hovered instanceof SectionEndComponent) return;
        Rect bounds = hovered.bounds();
        int hoveredAt = cells.indexOf(hovered);
        Rect column = hoveredAt < 0 ? bounds
                : union(cells, StorageOverlayLayout.stackStart(cells, hoveredAt), StorageOverlayLayout.stackEnd(cells, hoveredAt));
        Zone zone = zoneAt(hovered, cell, mouseX, mouseY);
        if (cell instanceof PageCardComponent && hovered instanceof PageCardComponent) {
            int band = stackBand(bounds);
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + band, zone == Zone.ABOVE ? INDICATOR_FILL : INDICATOR_HINT);
            graphics.fill(bounds.x(), bounds.bottom() - band, bounds.right(), bounds.bottom(), zone == Zone.BELOW ? INDICATOR_FILL : INDICATOR_HINT);
        }
        switch (zone) {
            case BEFORE -> graphics.fill(column.x(), column.y(), column.x() + INDICATOR_WIDTH, column.bottom(), INDICATOR_COLOR);
            case AFTER -> graphics.fill(column.right() - INDICATOR_WIDTH, column.y(), column.right(), column.bottom(), INDICATOR_COLOR);
            case ABOVE -> graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + INDICATOR_WIDTH, INDICATOR_COLOR);
            case BELOW -> graphics.fill(bounds.x(), bounds.bottom() - INDICATOR_WIDTH, bounds.right(), bounds.bottom(), INDICATOR_COLOR);
        }
    }

    private static Rect union(List<GridCellComponent> cells, int from, int to) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (int i = from; i < to; i++) {
            Rect r = cells.get(i).bounds();
            left = Math.min(left, r.x());
            top = Math.min(top, r.y());
            right = Math.max(right, r.right());
            bottom = Math.max(bottom, r.bottom());
        }
        return new Rect(left, top, right - left, bottom - top);
    }

    private static void outline(GuiGraphicsExtractor graphics, Rect r) {
        graphics.fill(r.x(), r.y(), r.right(), r.y() + 1, INDICATOR_COLOR);
        graphics.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), INDICATOR_COLOR);
        graphics.fill(r.x(), r.y(), r.x() + 1, r.bottom(), INDICATOR_COLOR);
        graphics.fill(r.right() - 1, r.y(), r.right(), r.bottom(), INDICATOR_COLOR);
    }

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
}
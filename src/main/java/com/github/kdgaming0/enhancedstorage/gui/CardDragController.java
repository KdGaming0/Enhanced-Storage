package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.gui.component.text.TextComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
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

    private final StorageOverlayLayout layout;
    private final Consumer<List<StorageKey>> onDrop;

    private @Nullable PageCardComponent dragged;
    private List<PageCardComponent> originalOrder = List.of();
    private @Nullable TextComponent ghostTitle;
    private double grabOffsetX;
    private double grabOffsetY;
    private int screenWidth;
    private int screenHeight;
    private long lastFrameMs;

    CardDragController(StorageOverlayLayout layout, Consumer<List<StorageKey>> onDrop) {
        this.layout = layout;
        this.onDrop = onDrop;
    }

    boolean isActive() {
        return dragged != null;
    }

    void start(PageCardComponent card, double pressX, double pressY, int screenWidth, int screenHeight) {
        this.dragged = card;
        this.originalOrder = List.copyOf(layout.getPageCards());
        this.grabOffsetX = pressX - card.getTotalX();
        this.grabOffsetY = pressY - card.getTotalY();
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.lastFrameMs = Util.getMillis();

        String name = PageNames.getInstance().get(card.getKey()).orElse(card.getKey().displayName());
        TextComponent title = new TextComponent(0, 0, Component.literal(name), OverlayTheme.titleColor());
        title.setDrawShadow(OverlayTheme.titleShadow());
        title.updateParentPosition(0, 0, screenWidth, screenHeight);
        this.ghostTitle = title;
    }

    void update(double mouseX, double mouseY) {
        PageCardComponent card = dragged;
        Rect viewport = layout.getPageOverviewBounds();
        if (card == null || viewport == null) return;

        List<PageCardComponent> order = new ArrayList<>(layout.getPageCards());
        PageCardComponent hovered = cardUnder(order, viewport, mouseX, mouseY);
        int to;
        if (hovered != null) {
            to = order.indexOf(hovered);
        } else if (isPastEnd(order, viewport, mouseX, mouseY)) {
            to = order.size() - 1;
        } else {
            // Gaps between cards keep the current slot, so the grid doesn't jitter.
            return;
        }

        int from = order.indexOf(card);
        if (from < 0 || from == to) return;
        order.remove(from);
        order.add(to, card);
        layout.previewOrder(order);
    }

    void cancel() {
        if (dragged != null && !layout.getPageCards().equals(originalOrder)) {
            layout.previewOrder(originalOrder);
        }
        reset();
    }

    void reset() {
        dragged = null;
        ghostTitle = null;
        originalOrder = List.of();
    }

    void drop() {
        if (dragged == null) return;
        List<PageCardComponent> finalOrder = List.copyOf(layout.getPageCards());
        boolean changed = !finalOrder.equals(originalOrder);
        reset();
        if (!changed) return;

        List<StorageKey> keys = new ArrayList<>();
        for (PageCardComponent card : finalOrder) {
            keys.add(card.getKey());
        }
        onDrop.accept(keys);
    }

    void paint(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        PageCardComponent card = dragged;
        Rect viewport = layout.getPageOverviewBounds();
        if (card == null || viewport == null) return;

        if (autoScroll(viewport, mouseY)) {
            // Cards slide under a still cursor while scrolling, so pick the slot again.
            update(mouseX, mouseY);
        }

        // The dragged card already sits in its landing slot; cover it with an empty active card to mark the drop spot.
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        Rect slot = card.bounds();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.PAGE_CARD_ACTIVE),
                slot.x(), slot.y(), slot.right() - slot.x(), slot.bottom() - slot.y());
        graphics.disableScissor();

        int ghostX = (int) (mouseX - grabOffsetX);
        int ghostY = (int) (mouseY - grabOffsetY);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.PAGE_CARD_IDLE),
                ghostX, ghostY, card.getWidth(), card.getHeight(), ARGB.white(GHOST_ALPHA));
        if (ghostTitle != null) {
            ghostTitle.setX(ghostX + GHOST_TITLE_OFFSET);
            ghostTitle.setY(ghostY + GHOST_TITLE_OFFSET);
            ghostTitle.extractRenderStateBase(graphics, mouseX, mouseY, 0, screenWidth, screenHeight);
        }
    }

    // Scrolls the page overview when the cursor is near the top or bottom edge of the viewport.
    /** Time-based, so speed doesn't depend on frame rate. Returns true if the content moved. */
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

    private static @Nullable PageCardComponent cardUnder(List<PageCardComponent> cards, Rect viewport, double x, double y) {
        if (!viewport.contains(x, y)) return null;
        for (PageCardComponent card : cards) {
            if (card.bounds().contains(x, y)) return card;
        }
        return null;
    }

    // Below the last card, or beside it in the last row, means "put it at the very end".
    private static boolean isPastEnd(List<PageCardComponent> cards, Rect viewport, double x, double y) {
        if (cards.isEmpty() || !viewport.contains(x, y)) return false;
        Rect last = cards.getLast().bounds();
        return y >= last.bottom() || (y >= last.y() && x >= last.right());
    }
}
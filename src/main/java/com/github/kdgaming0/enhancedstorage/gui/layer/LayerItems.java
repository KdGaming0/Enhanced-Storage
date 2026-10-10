package com.github.kdgaming0.enhancedstorage.gui.layer;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.component.page.TooltipItemComponent;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Animated items (enchant glint, animated textures) kept out of a {@link PageLayerCache#PAGES} layer and drawn on top
 * of it instead. Player heads whose skin is still loading are kept out the same way, so they update when it arrives
 * instead of showing the default skin until the page layer's next redraw.
 * <p>
 * A layer only changes when it is redrawn, so an animated item inside it would force the whole page layer to be
 * redrawn at the glint rate. Kept apart, only these items are redrawn at that rate, and the page layer only when
 * something it shows changes. With a limited glint rate they are drawn into their own {@link PageLayerCache#ANIMATED}
 * layer, redrawn at that rate, so a frame in between only blits it. On Smooth they are drawn live every frame.
 * <p>
 * An owner extracts its cells inside {@link #collect}. A cell about to draw an animated item calls {@link #defer}
 * instead, and leaves out the item and everything it draws over it. The owner keeps the result together with its
 * page layer and {@linkplain #extract draws} it after that layer, every frame.
 */
public final class LayerItems {

    public static final LayerItems NONE = new LayerItems();

    private static final int ITEM_SIZE = 16;
    // Vanilla layers each new GUI element by searching every earlier element of the current stratum, which is
    // quadratic in the element count. Items share a stratum in groups of this many.
    private static final int ITEMS_PER_STRATUM = 32;
    private static final ItemStackRenderState PROBE = new ItemStackRenderState();

    private static @Nullable LayerItems collecting;
    private static @Nullable Rect drawnHere;

    private static int ticks;
    private static int frameTicks;
    private static @Nullable GuiGraphicsExtractor frame;
    private static long frameStartedAt;
    private static long lastFrameMillis;
    private static boolean tickThisFrame;

    private final List<Entry> entries = new ArrayList<>();
    // When these items were last drawn into the animated layer, and that layer's generation then.
    private boolean inLayer;
    private int layerGeneration;
    private long layerDrawnAt;
    // A due refresh was put off by one frame (see postponeForTick).
    private boolean postponed;

    private LayerItems() {
    }

    /**
     * True when animated items are drawn live every frame (Smooth) rather than from the animated layer.
     */
    public static boolean drawnLive() {
        return EnhancedStorageConfig.animatedItemRefreshRate.intervalMillis <= 0;
    }

    /**
     * Runs {@code extraction} with animated items deferred and returns them. Items lying fully inside
     * {@code drawnHere} are drawn right away instead: that area is being drawn live, such as the hovered gap.
     */
    public static LayerItems collect(@Nullable Rect drawnHere, Runnable extraction) {
        LayerItems items = new LayerItems();
        LayerItems outerCollecting = collecting;
        Rect outerDrawnHere = LayerItems.drawnHere;
        collecting = items;
        LayerItems.drawnHere = drawnHere;
        try {
            extraction.run();
        } finally {
            collecting = outerCollecting;
            LayerItems.drawnHere = outerDrawnHere;
        }
        return items;
    }

    /**
     * Called by a cell about to draw {@code item}. True if the item is animated or still loading its skin, and is being
     * collected: the cell must then leave out the item and what it draws over it, and {@code drawer} draws them later.
     */
    public static boolean defer(TooltipItemComponent item, Drawer drawer) {
        LayerItems into = collecting;
        if (into == null || !(item.isAnimated() || isLoadingSkin(item.getItemStack()))) return false;
        Rect bounds = new Rect(item.getTotalX(), item.getTotalY(), ITEM_SIZE, ITEM_SIZE);
        if (drawnHere != null && inside(drawnHere, bounds)) return false;
        into.entries.add(new Entry(bounds, drawer));
        return true;
    }

    /**
     * True if {@code stack} is drawn animated in a GUI, i.e. the item atlas redraws it every frame.
     */
    public static boolean isAnimated(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getItemModelResolver().updateForTopItem(PROBE, stack, ItemDisplayContext.GUI, minecraft.level, null, 0);
        boolean animated = PROBE.isAnimated();
        PROBE.clear();
        return animated;
    }

    /**
     * True if {@code stack} is a player head whose skin hasn't finished loading. Vanilla draws it with the default skin
     * until then; a failed load also counts as finished.
     */
    public static boolean isLoadingSkin(ItemStack stack) {
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        return profile != null && !Minecraft.getInstance().playerSkinRenderCache().lookup(profile).isDone();
    }

    /**
     * Counts client ticks, so {@link #extract} can tell whether the current frame ran one.
     */
    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> ticks++);
    }

    // Vanilla makes a new main extractor every frame, so a different one means a new frame. Ticks run before the
    // frame is extracted, so a changed tick count means this frame ran one.
    private static void syncFrame(GuiGraphicsExtractor main, long now) {
        if (main == frame) return;
        frame = main;
        lastFrameMillis = now - frameStartedAt;
        frameStartedAt = now;
        tickThisFrame = ticks != frameTicks;
        frameTicks = ticks;
    }

    private static boolean inside(Rect outer, Rect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }

    /**
     * Draws the collected items over the owner's page layer, which covers the given screen rectangle.
     * {@code gap} is an area of it drawn live this frame, or null. When the items are {@linkplain #drawnLive drawn
     * live}, the gap's owner collects with it as {@code drawnHere}, and items lying fully inside it are skipped here.
     * Otherwise the gap is left out of the animated layer's blit, and the gap's owner draws everything in it.
     */
    public void extract(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, @Nullable Rect gap) {
        if (entries.isEmpty()) return;
        if (drawnLive()) {
            extractEntries(graphics, gap);
            return;
        }

        PageLayerCache layer = PageLayerCache.ANIMATED;
        int generation = layer.sync(graphics);
        long now = Util.getMillis();
        syncFrame(graphics, now);
        long interval = EnhancedStorageConfig.animatedItemRefreshRate.intervalMillis;
        boolean missing = !inLayer || generation != layerGeneration;
        if (missing || (now - layerDrawnAt >= interval && !postponeForTick(interval))) {
            postponed = false;
            GuiGraphicsExtractor layerGraphics = layer.beginLayer(graphics, x0, y0, x1 - x0, y1 - y0);
            extractEntries(layerGraphics, null);
            layer.endLayer(layerGraphics);
            inLayer = true;
            layerGeneration = generation;
            layerDrawnAt = now;
        }
        graphics.nextStratum();
        layer.blitAround(graphics, x0, y0, x1, y1, gap);
    }

    /**
     * True if a refresh that is due should wait one frame, because this frame already ran a client tick. Both are
     * among the slowest things a frame does, so letting them share a frame makes the worst frames worse. Only done
     * when frames are short next to the glint interval, so the one-frame delay can't be seen, and never twice in a row.
     */
    private boolean postponeForTick(long interval) {
        if (postponed || !tickThisFrame || lastFrameMillis > interval / 4) return false;
        postponed = true;
        return true;
    }

    private void extractEntries(GuiGraphicsExtractor graphics, @Nullable Rect skip) {
        int inStratum = ITEMS_PER_STRATUM;
        for (Entry entry : entries) {
            if (skip != null && inside(skip, entry.bounds())) continue;
            if (inStratum++ == ITEMS_PER_STRATUM) {
                graphics.nextStratum();
                inStratum = 1;
            }
            entry.drawer().extract(graphics);
        }
    }

    /**
     * Draws a deferred item and everything its cell draws over it. It is never hovered when drawn this way.
     */
    @FunctionalInterface
    public interface Drawer {
        void extract(GuiGraphicsExtractor graphics);
    }

    private record Entry(Rect bounds, Drawer drawer) {
    }
}

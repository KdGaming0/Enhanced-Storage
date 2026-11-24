package com.github.kdgaming0.enhancedstorage.compat;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime bridge for the SkyHanni compat mixins (mixin/compat/SkyHanni*Mixin).
 * <p>
 * SkyHanni gates its chest features (Chest Value etc.) on the open screen being
 * the vanilla ContainerScreen. StorageContainerScreen extends uilib's container
 * screen instead, so those checks fail even though the ChestMenu underneath is
 * untouched. The mixins OR our screen into those checks and hand SkyHanni the
 * real container slots.
 * <p>
 * Every entry point is wrapped so a SkyHanni-side change degrades to a single
 * logged warning instead of a crash.
 */
public final class SkyHanniCompat {

    private static final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    private static final String CHEST_VALUE = "at.hannibal2.skyhanni.features.inventory.ChestValue";
    private static final String RENDERABLE = "at.hannibal2.skyhanni.utils.renderables.Renderable";
    private static final String DUNGEON_API = "at.hannibal2.skyhanni.features.dungeon.DungeonApi";
    private static final String ESTIMATED_ITEM_VALUE = "at.hannibal2.skyhanni.features.misc.items.EstimatedItemValue";
    /** Gap between the panel and the screen edge, and between the panel and the pages. */
    private static final int CHEST_VALUE_MARGIN = 4;
    /** Space between the panel's edge and the list inside it. */
    private static final int CHEST_VALUE_PADDING = 5;
    /** How long after SkyHanni last drew its list it still counts as showing. */
    private static final long CHEST_VALUE_SHOWN_MS = 500;
    private static final String CHEST_VALUE_EMPTY_TEXT = "Nothing to value on this page.";
    private static final boolean SKYHANNI_LOADED = FabricLoader.getInstance().isModLoaded("skyhanni");

    // Chest Value list placement: set while SkyHanni's ChestValue draws (see SkyHanniChestValuePositionMixin), and
    // where the storage screen wants it (screen coordinates; set by each layout).
    private static boolean drawingChestValue;
    private static int chestValueMinX;
    private static int chestValueRight;
    private static int chestValueY;
    private static int chestValueScreenHeight;
    // Whether the list has been measured during the current draw, and whether that worked. SkyHanni reads X and Y
    // (each maybe more than once) per draw; both go by the one measurement so they always agree.
    private static boolean chestValueMeasured;
    private static boolean chestValueMeasuredOk;
    // When SkyHanni last drew the list on our screen, the widest it has been since the storage menu opened, and its
    // current height (screen pixels, at SkyHanni's scale).
    private static long chestValueDrawnAt;
    private static int chestValueWidth;
    private static int chestValueHeight;
    private static boolean chestValueResolved;
    private static boolean chestValueFailed;
    private static @Nullable Field chestValueDisplay;
    private static @Nullable Object chestValueInstance;
    private static @Nullable Method chestValueConfig;
    private static @Nullable Method renderableWidth;
    private static @Nullable Method renderableHeight;
    private static @Nullable Method chestValueIsEnabled;
    private static @Nullable Method configPosition;
    private static @Nullable Method positionScale;
    // SkyHanni's other reasons to hide the list. Optional: when one can't be found, that check is skipped.
    private static @Nullable Method configEnableInDungeons;
    private static @Nullable Object dungeonApi;
    private static @Nullable Method inDungeon;
    private static @Nullable Method configShowDuringEiv;
    private static @Nullable Object estimatedItemValue;
    private static @Nullable Method eivShowing;

    private SkyHanniCompat() {
    }

    /**
     * true when the currently open screen is our storage screen.
     */
    public static boolean isStorageScreenOpen() {
        try {
            return Minecraft.getInstance().screen instanceof StorageContainerScreen;
        } catch (Throwable t) {
            reportFailure("isStorageScreenOpen", t);
            return false;
        }
    }

    /**
     * Container (non-player-inventory) slots of the open storage screen,
     * mirroring what SkyHanni's InventoryUtils.getItemsInOpenChestWithNull()
     * returns for a vanilla chest screen. Returns null when the storage screen
     * is not open (or on failure) so the caller keeps SkyHanni's original
     * behavior.
     */
    public static List<Slot> containerSlotsOfOpenStorageScreen() {
        try {
            Screen screen = Minecraft.getInstance().screen;
            if (!(screen instanceof StorageContainerScreen storageScreen)) {
                return null;
            }
            List<Slot> slots = new ArrayList<>();
            for (Slot slot : storageScreen.getMenu().slots) {
                if (!(slot.container instanceof Inventory)) {
                    slots.add(slot);
                }
            }
            return slots;
        } catch (Throwable t) {
            reportFailure("containerSlotsOfOpenStorageScreen", t);
            return null;
        }
    }

    /**
     * Whether SkyHanni's Chest Value list goes beside the pages (our setting) rather than at SkyHanni's position.
     */
    private static boolean chestValueBesidePages() {
        return EnhancedStorageConfig.skyHanniChestValuePosition == EnhancedStorageConfig.SkyHanniChestValuePosition.BESIDE_PAGES;
    }

    /**
     * Whether SkyHanni's Chest Value may show on the storage screen at all (our setting isn't Hidden).
     */
    public static boolean chestValueAllowed() {
        return EnhancedStorageConfig.skyHanniChestValuePosition != EnhancedStorageConfig.SkyHanniChestValuePosition.HIDDEN;
    }

    /**
     * Whether the storage screen gets a Chest Value panel: SkyHanni is installed and would show Chest Value here (it's
     * turned on, we're in SkyBlock, and not in a dungeon unless it's allowed there), our setting is Beside Pages, and
     * nothing has failed.
     */
    private static boolean chestValuePanelEnabled() {
        if (!SKYHANNI_LOADED || chestValueFailed || !chestValueBesidePages() || !resolveChestValue()) return false;
        try {
            if (!(boolean) chestValueIsEnabled.invoke(chestValueInstance)) return false;
            return !hiddenInDungeon();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            chestValueFailed = true;
            reportFailure("readChestValueEnabled", e);
            return false;
        }
    }

    private static boolean hiddenInDungeon() {
        if (inDungeon == null || configEnableInDungeons == null) return false;
        try {
            if (!(boolean) inDungeon.invoke(dungeonApi)) return false;
            return !(boolean) configEnableInDungeons.invoke(chestValueConfig.invoke(null, chestValueInstance));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            inDungeon = null;
            reportFailure("readChestValueDungeon", e);
            return false;
        }
    }

    // Whether SkyHanni is hiding the list for now because an Estimated Item Value tooltip shows.
    private static boolean hiddenByEstimatedItemValue() {
        if (eivShowing == null || configShowDuringEiv == null) return false;
        try {
            if (!(boolean) eivShowing.invoke(estimatedItemValue)) return false;
            return !(boolean) configShowDuringEiv.invoke(chestValueConfig.invoke(null, chestValueInstance));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            eivShowing = null;
            reportFailure("readChestValueEstimatedItemValue", e);
            return false;
        }
    }

    /**
     * Where the panel goes, from the storage screen's layout: its right side at {@code right} (the overlay's left edge
     * less the gap between overlay panels), never further left than {@code leftEdge} (the screen edge or another
     * mod's column), and level with the top of the pages, moved up if it would go past the bottom of the screen.
     */
    public static void placeChestValue(int leftEdge, int right, int top, int screenHeight) {
        chestValueMinX = leftEdge + CHEST_VALUE_MARGIN;
        chestValueRight = right;
        chestValueY = top;
        chestValueScreenHeight = screenHeight;
    }

    /**
     * Forgets how wide the list has been, so the next storage screen sizes the panel afresh. Called when the storage
     * menu really closes, not on a page switch, so the pages don't move while switching.
     */
    public static void onStorageScreenClosed() {
        chestValueWidth = 0;
    }

    private static int chestValuePanelWidth() {
        int message = Minecraft.getInstance().font.width(CHEST_VALUE_EMPTY_TEXT);
        return Math.max(chestValueWidth, message) + 2 * CHEST_VALUE_PADDING;
    }

    private static int chestValuePanelX() {
        return Math.max(chestValueMinX, chestValueRight - chestValuePanelWidth());
    }

    private static int chestValuePanelHeight() {
        int content = chestValueListShowing() ? chestValueHeight : Minecraft.getInstance().font.lineHeight;
        return content + 2 * CHEST_VALUE_PADDING;
    }

    private static int chestValuePanelY() {
        int lowest = chestValueScreenHeight - CHEST_VALUE_MARGIN - chestValuePanelHeight();
        return Math.max(CHEST_VALUE_MARGIN, Math.min(chestValueY, lowest));
    }

    // Whether SkyHanni drew its list recently. It skips frames now and then (an Estimated Item Value tooltip hides it),
    // so a short gap doesn't count as gone.
    private static boolean chestValueListShowing() {
        return Util.getMillis() - chestValueDrawnAt <= CHEST_VALUE_SHOWN_MS;
    }

    /**
     * Room the panel needs from the left edge it's placed against (its width plus a margin on both sides), or 0 when
     * there is no panel. The panel stays while the list comes and goes, and only ever grows, so the pages don't move
     * when switching pages.
     */
    public static int chestValueExtent() {
        if (!chestValuePanelEnabled()) return 0;
        return CHEST_VALUE_MARGIN + chestValuePanelWidth() + CHEST_VALUE_MARGIN;
    }

    /**
     * Screen bounds of the panel, or null when the storage screen has none.
     */
    public static @Nullable Rect chestValuePanel() {
        if (!isStorageScreenOpen() || !chestValuePanelEnabled()) return null;
        // Keep the list's space while an item tooltip hides it, rather than flashing the empty note.
        if (chestValueListShowing() && hiddenByEstimatedItemValue()) chestValueDrawnAt = Util.getMillis();
        return new Rect(chestValuePanelX(), chestValuePanelY(), chestValuePanelWidth(), chestValuePanelHeight());
    }

    /**
     * The note shown in the panel when SkyHanni has no list for this page, or null while the list shows.
     */
    public static @Nullable String chestValueEmptyText() {
        return chestValueListShowing() ? null : CHEST_VALUE_EMPTY_TEXT;
    }

    public static int chestValuePadding() {
        return CHEST_VALUE_PADDING;
    }

    /**
     * Brackets SkyHanni's ChestValue.onChestGuiRender, which only gets to the position lookups below when it has a list
     * to draw.
     */
    public static void beginChestValueDraw() {
        drawingChestValue = true;
        chestValueMeasured = false;
    }

    public static void endChestValueDraw() {
        drawingChestValue = false;
    }

    /**
     * Our X for the list while ChestValue draws on the storage screen with the setting on, else null so SkyHanni uses
     * its own. SkyHanni reads it for both where it draws and where the mouse is, so hovering and clicking follow.
     */
    public static @Nullable Integer chestValueAbsX() {
        if (!overridingChestValue() || !measureChestValueOnce()) return null;
        return chestValuePanelX() + CHEST_VALUE_PADDING;
    }

    public static @Nullable Integer chestValueAbsY() {
        if (!overridingChestValue() || !measureChestValueOnce()) return null;
        return chestValuePanelY() + CHEST_VALUE_PADDING;
    }

    private static boolean measureChestValueOnce() {
        if (!chestValueMeasured) {
            chestValueMeasured = true;
            chestValueMeasuredOk = noteChestValueDrawn();
        }
        return chestValueMeasuredOk;
    }

    private static boolean overridingChestValue() {
        return drawingChestValue && chestValueBesidePages() && isStorageScreenOpen();
    }

    // Records that the list is showing and how big it is, scaled like SkyHanni draws it. False when it can't be
    // measured: then there is no panel, so the list stays at SkyHanni's own position rather than over the pages.
    private static boolean noteChestValueDrawn() {
        if (chestValueFailed) return false;
        try {
            if (!resolveChestValue()) {
                chestValueFailed = true;
                return false;
            }
            List<?> display = (List<?>) chestValueDisplay.get(null);
            int width = 0;
            int height = 0;
            for (Object renderable : display) {
                width = Math.max(width, (int) renderableWidth.invoke(renderable));
                // RenderUtils.renderRenderables steps each line down by its height + 1 for Chest Value.
                height += (int) renderableHeight.invoke(renderable) + 1;
            }
            Object position = configPosition.invoke(chestValueConfig.invoke(null, chestValueInstance));
            float scale = (float) positionScale.invoke(position);
            chestValueWidth = Math.max(chestValueWidth, (int) Math.ceil(width * scale));
            chestValueHeight = (int) Math.ceil(Math.max(0, height - 1) * scale);
            chestValueDrawnAt = Util.getMillis();
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            chestValueFailed = true;
            reportFailure("measureChestValue", e);
            return false;
        }
    }

    private static boolean resolveChestValue() {
        if (chestValueResolved) return chestValueDisplay != null;
        chestValueResolved = true;
        try {
            Class<?> chestValue = Class.forName(CHEST_VALUE);
            Field display = chestValue.getDeclaredField("display");
            display.setAccessible(true);
            chestValueInstance = chestValue.getField("INSTANCE").get(null);
            chestValueConfig = chestValue.getMethod("access$getConfig", chestValue);
            Method isEnabled = chestValue.getDeclaredMethod("isEnabled");
            isEnabled.setAccessible(true);
            chestValueIsEnabled = isEnabled;
            Class<?> config = chestValueConfig.getReturnType();
            configPosition = config.getMethod("getPosition");
            positionScale = configPosition.getReturnType().getMethod("getEffectiveScale");
            Class<?> renderable = Class.forName(RENDERABLE);
            renderableWidth = renderable.getMethod("getWidth");
            renderableHeight = renderable.getMethod("getHeight");
            chestValueDisplay = display;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            reportFailure("resolveChestValue", e);
            return false;
        }
        resolveChestValueGates(chestValueConfig.getReturnType());
        return true;
    }

    private static void resolveChestValueGates(Class<?> config) {
        try {
            Class<?> dungeon = Class.forName(DUNGEON_API);
            dungeonApi = dungeon.getField("INSTANCE").get(null);
            configEnableInDungeons = config.getMethod("getEnableInDungeons");
            inDungeon = dungeon.getMethod("inDungeon");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            inDungeon = null;
            reportFailure("resolveChestValueDungeon", e);
        }
        try {
            Class<?> eiv = Class.forName(ESTIMATED_ITEM_VALUE);
            estimatedItemValue = eiv.getField("INSTANCE").get(null);
            configShowDuringEiv = config.getMethod("getShowDuringEstimatedItemValue");
            eivShowing = eiv.getMethod("isCurrentlyShowing");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            eivShowing = null;
            reportFailure("resolveChestValueEstimatedItemValue", e);
        }
    }

    /**
     * Logs a hook failure once per session instead of spamming or crashing. The hook keeps falling back to
     * SkyHanni's own behavior each time it fails.
     */
    public static void reportFailure(String hook, Throwable t) {
        if (reportedFailures.add(hook)) {
            EnhancedStorage.LOGGER.warn(
                    "SkyHanni compat hook '{}' failed; falling back to SkyHanni's own behavior. "
                            + "SkyHanni chest features may not show on Enhanced Storage screens. "
                            + "This usually means a SkyHanni update changed its internals.",
                    hook, t);
        }
    }
}

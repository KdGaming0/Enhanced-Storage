package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.mixin.AbstractContainerScreenAccessor;
import com.github.kdgaming0.enhancedstorage.mixin.ScreenAccessor;
import com.github.kdgaming0.enhancedstorage.mixin.SlotAccessor;
import com.github.kdgaming0.enhancedstorage.storage.StorageData;
import com.github.kdgaming0.enhancedstorage.storage.StoragePage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Renders a persistent scrollable dashboard of all known storage pages on top of any
 * Hypixel storage container screen, replacing the vanilla chest background.
 *
 * <p>The same instance survives across screen transitions to preserve scroll and search state.
 * Use {@link #createOrAttach} and {@link #destroyActive} to manage the singleton lifecycle.
 */
public class StorageOverlay {

    // ── Sprites ───────────────────────────────────────────────────────────────
    private static final Identifier SLOT_HIGHLIGHT_BACK_SPRITE =
            Identifier.fromNamespaceAndPath("enhanced_storage", "storage_slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT_SPRITE =
            Identifier.fromNamespaceAndPath("enhanced_storage", "storage_slot_highlight_front");
    private static final Identifier SCROLLER_SPRITE =
            Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("widget/scroller_background");
    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int SLOT_SIZE = 18;
    private static final int PAGE_WIDTH = SLOT_SIZE * 9 + 6;
    private static final int PADDING = 8;
    private static final int SCROLL_BAR_W = 6;
    private static final int SCROLL_KNOB_MIN_H = 32;
    private static final int MIN_OVERLAY_H = 60;
    private static final int PANEL_TOP = SLOT_SIZE;
    private static final int INV_SLOTS_TOP = 15;
    private static final int STORAGE_INV_W = 176;
    private static final int STORAGE_INV_H = 97;
    private static final int BOTTOM_PADDING = 20;
    /**
     * Hypixel adds a navigation row at the top of every storage page; skip it when rendering.
     */
    private static final int NAV_ROW_COUNT = 1;
    private static final int NAV_ROW_SKIP_SLOTS = NAV_ROW_COUNT * 9;
    /**
     * Pixel offset from the card left edge to where the slot grid begins.
     */
    private static final int CARD_SLOTS_INSET_X = 3;
    /**
     * Pixel offset from the card top edge (below title) to where the slot grid begins.
     */
    private static final int CARD_SLOTS_INSET_Y = 6;
    /**
     * Base scroll pixels per mouse-wheel notch; multiplied by config scrollSpeed.
     */
    private static final float SCROLL_BASE = 30f;
    // Navigation panel (overview texture to the left of the inventory panel)
    private static final int NAV_PANEL_W = 176;
    private static final int NAV_PANEL_H = 85;
    private static final int NAV_PANEL_SLOT_X = 8;
    private static final int NAV_PANEL_EC_LABEL_Y = 4;
    private static final int NAV_PANEL_EC_ROW_Y = 15;
    private static final int NAV_PANEL_BP_LABEL_Y = 33;
    private static final int NAV_PANEL_BP_ROW1_Y = 43;
    private static final int NAV_PANEL_BP_ROW2_Y = 61;
    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int COLOR_TITLE_ACTIVE = 0xFF7AB4FF;
    private static final int COLOR_TITLE_IDLE = 0xFFA0AABB;
    private static final int COLOR_PLACEHOLDER = 0xFF505868;
    private static final int COLOR_DIM_OVERLAY = 0x88111111;
    // ── Singleton ─────────────────────────────────────────────────────────────
    private static StorageOverlay activeInstance;
    // Cached layout rects — recomputed in recalculateMeasurements, avoids per-frame allocation.
    private final Rect scrollPanelRect = new Rect(0, 0, 0, 0);
    private final Rect scrollbarTrackRect = new Rect(0, 0, 0, 0);
    // ── Screen references (null while detached) ───────────────────────────────
    private AbstractContainerScreen<?> screen;
    private AbstractContainerScreenAccessor accessor;
    private StoragePage activePage;
    private Minecraft mc;
    // ── Layout state (recomputed on every init) ───────────────────────────────
    private boolean isOverview;
    private int baseTopPos;
    private int[] originalPlayerSlotRelY;
    private int playerPush;
    private int pageWidthCount;
    private int overviewX, overviewWidth, overviewHeight;
    private int innerScrollPanelWidth, innerScrollPanelHeight;
    private int invPanelX, invPanelY;
    private int navPanelX, navPanelY;
    // ── Scroll state ──────────────────────────────────────────────────────────
    private float scroll;
    private int lastRenderedContentH;
    private boolean knobGrabbed;
    // ── Search state ──────────────────────────────────────────────────────────
    private EditBox searchField;
    private String searchQuery = "";
    // ── Highlight color cache (avoids parsing the config hex string every frame) ──
    private String cachedHighlightHex;
    private int cachedHighlightColor;
    private StorageOverlay(AbstractContainerScreen<?> screen, StoragePage activePage) {
        attach(screen, activePage);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves a themed sprite identifier based on the current {@link EnhancedStorageConfig#overlayTheme}.
     */
    private static Identifier sprite(String base) {
        return Identifier.fromNamespaceAndPath("enhanced_storage", base + EnhancedStorageConfig.overlayTheme.suffix());
    }

    /**
     * Returns the current overlay if one exists, otherwise creates a new one.
     * Preserves scroll and search state across screen transitions.
     */
    public static StorageOverlay createOrAttach(AbstractContainerScreen<?> screen, StoragePage activePage) {
        if (activeInstance == null) {
            activeInstance = new StorageOverlay(screen, activePage);
        } else {
            activeInstance.attach(screen, activePage);
        }
        return activeInstance;
    }

    /**
     * Fully tears down the active overlay. Call when leaving a storage context.
     */
    public static void destroyActive() {
        if (activeInstance != null) {
            activeInstance.detach();
            activeInstance = null;
        }
    }

    private static void hideSlot(Slot slot) {
        ((SlotAccessor) slot).es$setX(-9999);
        ((SlotAccessor) slot).es$setY(-9999);
    }

    /**
     * Converts a #RRGGBB or #RRGGBBAA hex string to an ARGB int.
     */
    private static int parseColor(String hex) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
            if (h.length() == 8) {
                int v = (int) Long.parseLong(h, 16);
                return (v >> 8) | ((v & 0xFF) << 24);
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFFFFFFFF;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Retargets this overlay to a new screen and page without resetting scroll or search.
     */
    public void attach(AbstractContainerScreen<?> screen, StoragePage activePage) {
        detach();
        this.screen = screen;
        this.accessor = (AbstractContainerScreenAccessor) screen;
        this.activePage = activePage;
        this.mc = Minecraft.getInstance();
        this.isOverview = activePage == null;
    }

    /**
     * Removes the search field from the old screen and clears transient references.
     */
    public void detach() {
        if (screen != null && searchField != null) {
            ((ScreenAccessor) screen).es$removeWidget(searchField);
        }
        if (searchField != null) {
            searchField.setFocused(false);
        }
        screen = null;
        accessor = null;
        activePage = null;
    }

    public void onInit(int screenWidth, int screenHeight) {
        ensureAllPagesRegistered();
        baseTopPos = accessor.es$getTopPos();
        originalPlayerSlotRelY = capturePlayerSlotRelY();
        recalculateMeasurements();
        scroll = clampScroll(scroll);

        if (activePage != null && EnhancedStorageConfig.autoScrollToActivePage) {
            scrollToPageIfOffScreen(activePage);
        }

        initSearchField();
        computeNavPanelLayout();
    }

    public void preRender(int mouseX, int mouseY) {
        pushPlayerSlots();
        if (isOverview) {
            repositionOverviewSlots();
        } else {
            repositionChestSlots();
        }
    }

    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        drawMainPanel(gfx);
        drawScrollableContent(gfx, mouseX, mouseY);
        drawScrollbar(gfx);
        drawInventoryLabel(gfx);
        if (searchField != null) {
            searchField.extractWidgetRenderState(gfx, mouseX, mouseY, delta);
        }
        drawNavigationPanel(gfx, mouseX, mouseY);
    }

    // ── Input delegation ──────────────────────────────────────────────────────

    public List<Rect> getBounds() {
        return List.of(
                new Rect(overviewX, PANEL_TOP, overviewWidth, overviewHeight),
                new Rect(navPanelX, navPanelY, NAV_PANEL_W, NAV_PANEL_H),
                new Rect(invPanelX, invPanelY, STORAGE_INV_W, STORAGE_INV_H));
    }

    public EditBox getSearchField() {
        return searchField;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (handleSearchFieldClick(event, doubleClick)) return true;
        if (handleScrollbarClick(event)) return true;
        // On the overview screen, Hypixel's real slots handle nav-panel clicks.
        if (!isOverview && handleNavPanelClick(event)) return true;
        return handlePageCardClick(event);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (knobGrabbed) {
            knobGrabbed = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!knobGrabbed) return false;
        scroll = scrollForKnobY(event.y());
        return true;
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!scrollPanelRect.contains(x, y)) return false;
        float dir = EnhancedStorageConfig.inverseScroll ? 1f : -1f;
        scroll = clampScroll(scroll + (float) (scrollY * EnhancedStorageConfig.scrollSpeed * SCROLL_BASE * dir));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (searchField == null || !searchField.isFocused()) return false;
        if (searchField.keyPressed(event)) return true;
        // Consume non-Escape keys while search is focused to suppress vanilla hotkeys (e.g. "E").
        return event.key() != GLFW.GLFW_KEY_ESCAPE;
    }

    private void recalculateMeasurements() {
        pageWidthCount = Math.clamp(
                (screen.width - PADDING * 2) / (PAGE_WIDTH + PADDING),
                1, EnhancedStorageConfig.overlayColumns);

        innerScrollPanelWidth = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING;
        overviewWidth = innerScrollPanelWidth + SCROLL_BAR_W + PADDING * 3;
        overviewX = screen.width / 2 - overviewWidth / 2;

        invPanelX = accessor.es$getLeftPos();
        invPanelY = screen.height - BOTTOM_PADDING - STORAGE_INV_H;

        overviewHeight = Math.max(MIN_OVERLAY_H, invPanelY - PANEL_TOP);
        innerScrollPanelHeight = overviewHeight - PADDING * 2;

        if (originalPlayerSlotRelY.length > 0) {
            int targetFirstSlotScreenY = invPanelY + INV_SLOTS_TOP;
            int vanillaFirstSlotScreenY = baseTopPos + originalPlayerSlotRelY[0];
            playerPush = targetFirstSlotScreenY - vanillaFirstSlotScreenY;
        }

        scrollPanelRect.x = overviewX + PADDING;
        scrollPanelRect.y = PANEL_TOP + PADDING;
        scrollPanelRect.width = innerScrollPanelWidth;
        scrollPanelRect.height = innerScrollPanelHeight;

        scrollbarTrackRect.x = overviewX + overviewWidth - PADDING - SCROLL_BAR_W;
        scrollbarTrackRect.y = PANEL_TOP + PADDING;
        scrollbarTrackRect.width = SCROLL_BAR_W;
        scrollbarTrackRect.height = innerScrollPanelHeight;
    }

    // ── Slot repositioning ────────────────────────────────────────────────────

    private void computeNavPanelLayout() {
        navPanelX = invPanelX - NAV_PANEL_W;
        navPanelY = invPanelY;
        if (navPanelX < 8) {
            // Fall back to centering above the inventory when the screen is too narrow.
            navPanelX = (screen.width - NAV_PANEL_W) / 2;
            navPanelY = invPanelY - NAV_PANEL_H;
        }
    }

    private void scrollToPageIfOffScreen(StoragePage page) {
        Rect pageRect = findPageRect(page);
        if (pageRect == null) return;
        int pageTop = pageRect.y - (int) scroll;
        int pageBottom = pageTop + pageRect.height;
        boolean offScreen = pageTop >= scrollPanelRect.y + scrollPanelRect.height
                || pageBottom <= scrollPanelRect.y;
        if (offScreen) {
            scroll = clampScroll(pageRect.y - scrollPanelRect.y);
        }
    }

    private void pushPlayerSlots() {
        List<Slot> playerSlots = getPlayerSlots();
        for (int i = 0; i < Math.min(playerSlots.size(), originalPlayerSlotRelY.length); i++) {
            ((SlotAccessor) playerSlots.get(i)).es$setY(originalPlayerSlotRelY[i] + playerPush);
        }
    }

    private void repositionChestSlots() {
        if (mc.player == null) return;
        Rect activeRect = findPageRect(activePage);
        int leftPos = accessor.es$getLeftPos();

        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue;
            if (activePage == null || activeRect == null || slot.index < NAV_ROW_SKIP_SLOTS) {
                hideSlot(slot);
                continue;
            }

            int visIndex = slot.index - NAV_ROW_SKIP_SLOTS;
            int screenX = activeRect.x + CARD_SLOTS_INSET_X + 1 + (visIndex % 9) * SLOT_SIZE;
            int screenY = activeRect.y + mc.font.lineHeight + CARD_SLOTS_INSET_Y + 1
                    + (visIndex / 9) * SLOT_SIZE - (int) scroll;

            boolean inPanel = screenY >= scrollPanelRect.y
                    && screenY + SLOT_SIZE <= scrollPanelRect.y + scrollPanelRect.height;

            if (inPanel) {
                ((SlotAccessor) slot).es$setX(screenX - leftPos);
                ((SlotAccessor) slot).es$setY(screenY - baseTopPos);
            } else {
                hideSlot(slot);
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void repositionOverviewSlots() {
        if (mc.player == null) return;
        NavPanelCoords c = computeNavPanelCoords();
        int leftPos = accessor.es$getLeftPos();

        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue;

            if (slot.index >= StoragePage.OVERVIEW_EC_SLOT_FIRST
                    && slot.index <= StoragePage.OVERVIEW_EC_SLOT_LAST) {
                int col = slot.index - StoragePage.OVERVIEW_EC_SLOT_FIRST;
                ((SlotAccessor) slot).es$setX(c.slotStartX() + col * SLOT_SIZE - leftPos);
                ((SlotAccessor) slot).es$setY(c.ecRowY() - baseTopPos);
            } else if (slot.index >= StoragePage.OVERVIEW_BP_ROW1_SLOT_FIRST
                    && slot.index <= StoragePage.OVERVIEW_BP_ROW1_SLOT_LAST) {
                int col = slot.index - StoragePage.OVERVIEW_BP_ROW1_SLOT_FIRST;
                ((SlotAccessor) slot).es$setX(c.slotStartX() + col * SLOT_SIZE - leftPos);
                ((SlotAccessor) slot).es$setY(c.bp1RowY() - baseTopPos);
            } else if (slot.index >= StoragePage.OVERVIEW_BP_ROW2_SLOT_FIRST
                    && slot.index <= StoragePage.OVERVIEW_BP_ROW2_SLOT_LAST) {
                int col = slot.index - StoragePage.OVERVIEW_BP_ROW2_SLOT_FIRST;
                ((SlotAccessor) slot).es$setX(c.slotStartX() + col * SLOT_SIZE - leftPos);
                ((SlotAccessor) slot).es$setY(c.bp2RowY() - baseTopPos);
            } else {
                hideSlot(slot);
            }
        }
    }

    private void drawMainPanel(GuiGraphicsExtractor gfx) {
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("main_panel"),
                overviewX, PANEL_TOP, overviewWidth, overviewHeight);
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("storage_inventory"),
                invPanelX, invPanelY, STORAGE_INV_W, STORAGE_INV_H);
    }

    private void drawScrollableContent(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        gfx.enableScissor(scrollPanelRect.x, scrollPanelRect.y,
                scrollPanelRect.x + scrollPanelRect.width,
                scrollPanelRect.y + scrollPanelRect.height);
        gfx.pose().pushMatrix();
        gfx.pose().translate(0f, -scroll);
        forEachPage(getFilteredPages(), (rect, page, inv) ->
                drawPageCard(gfx, rect, page, inv, page.equals(activePage), mouseX, mouseY));
        gfx.pose().popMatrix();
        gfx.disableScissor();
    }

    private void drawPageCard(GuiGraphicsExtractor gfx, Rect rect, StoragePage page,
                              StorageData.StorageInventory inv, boolean isActive,
                              int mouseX, int mouseY) {
        int rows = pageRows(page, inv, isActive);
        boolean hasInv = inv != null && inv.inventory() != null;
        int cardH = pageCardHeight(rows, hasInv);

        Identifier cardSprite = isActive ? sprite("page_card_active") : sprite("page_card_idle");
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, cardSprite, rect.x, rect.y, PAGE_WIDTH, cardH);

        String title = inv != null && inv.title() != null ? inv.title() : page.defaultName();
        gfx.text(mc.font, title, rect.x + 6, rect.y + 5,
                isActive ? COLOR_TITLE_ACTIVE : COLOR_TITLE_IDLE, true);

        if (!hasInv) {
            gfx.centeredText(mc.font, "Open Page",
                    rect.x + PAGE_WIDTH / 2, rect.y + cardH / 2, COLOR_PLACEHOLDER);
            return;
        }

        drawSlotBackgrounds(gfx, rect, rows);
        if (!isActive) {
            drawFakeItems(gfx, rect, inv.inventory().stacks(), rows, mouseX, mouseY);
        }
    }

    private void drawSlotBackgrounds(GuiGraphicsExtractor gfx, Rect pageRect, int rows) {
        for (int i = 0; i < rows * 9; i++) {
            int x = pageRect.x + CARD_SLOTS_INSET_X + (i % 9) * SLOT_SIZE;
            int y = pageRect.y + mc.font.lineHeight + CARD_SLOTS_INSET_Y + (i / 9) * SLOT_SIZE;
            gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("storage_slot"), x, y, SLOT_SIZE, SLOT_SIZE);
        }
    }

    private void drawFakeItems(GuiGraphicsExtractor gfx, Rect pageRect, List<ItemStack> stacks,
                               int rows, int mouseX, int mouseY) {
        int startIdx = NAV_ROW_SKIP_SLOTS;
        int endIdx = Math.min(stacks.size(), startIdx + rows * 9);
        int contentMouseY = mouseY + (int) scroll;
        boolean searching = !searchQuery.isBlank();
        int highlightColor = searching ? getSearchHighlightColor() : 0;

        for (int i = startIdx; i < endIdx; i++) {
            ItemStack stack = stacks.get(i);
            int visIndex = i - startIdx;
            int slotX = pageRect.x + CARD_SLOTS_INSET_X + 1 + (visIndex % 9) * SLOT_SIZE;
            int slotY = pageRect.y + mc.font.lineHeight + CARD_SLOTS_INSET_Y + 1
                    + (visIndex / 9) * SLOT_SIZE;
            boolean hovered = isSlotHovered(slotX, slotY, mouseX, contentMouseY);

            if (hovered) {
                gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_SPRITE,
                        slotX - 4, slotY - 4, 24, 24);
            }

            if (searching) {
                int tint = matchesSearch(stack, searchQuery) ? highlightColor : COLOR_DIM_OVERLAY;
                gfx.fill(slotX, slotY, slotX + SLOT_SIZE - 2, slotY + SLOT_SIZE - 2, tint);
            }

            if (stack.isEmpty()) continue;

            gfx.fakeItem(stack, slotX, slotY);
            gfx.itemDecorations(mc.font, stack, slotX, slotY);

            if (hovered && EnhancedStorageConfig.showPreviewTooltips) {
                gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_SPRITE,
                        slotX - 4, slotY - 4, 24, 24);
                gfx.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
            }
        }
    }

    private void drawInventoryLabel(GuiGraphicsExtractor gfx) {
        gfx.text(mc.font, Component.translatable("container.inventory"),
                invPanelX + 8, invPanelY + 4, COLOR_TITLE_IDLE, false);
    }

    private void drawNavigationPanel(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("storage_overview"),
                navPanelX, navPanelY, NAV_PANEL_W, NAV_PANEL_H);
        gfx.text(mc.font, "Ender Chest",
                navPanelX + 8, navPanelY + NAV_PANEL_EC_LABEL_Y, COLOR_TITLE_ACTIVE, false);
        gfx.text(mc.font, "Backpacks",
                navPanelX + 8, navPanelY + NAV_PANEL_BP_LABEL_Y, COLOR_TITLE_ACTIVE, false);

        // On the overview screen, real Hypixel slots are mapped to the panel — no fake items needed.
        if (!isOverview) {
            drawNavPanelItems(gfx, mouseX, mouseY);
        }
    }

    private void drawNavPanelItems(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        NavPanelCoords c = computeNavPanelCoords();

        for (int i = 0; i < StoragePage.ENDER_CHEST_COUNT; i++) {
            drawNavPanelItem(gfx, StoragePage.ofEnderChest(i + 1),
                    c.slotStartX() + i * SLOT_SIZE, c.ecRowY(), mouseX, mouseY);
        }

        for (int i = 0; i < StoragePage.BACKPACK_COUNT; i++) {
            int sy = (i / 9 == 0) ? c.bp1RowY() : c.bp2RowY();
            drawNavPanelItem(gfx, StoragePage.ofBackpack(i + 1),
                    c.slotStartX() + (i % 9) * SLOT_SIZE, sy, mouseX, mouseY);
        }
    }

    private void drawNavPanelItem(GuiGraphicsExtractor gfx, StoragePage page,
                                  int sx, int sy, int mouseX, int mouseY) {
        ItemStack stack = getRepresentativeStack(page);
        if (stack.isEmpty()) return;

        boolean hovered = mouseX >= sx && mouseX < sx + SLOT_SIZE
                && mouseY >= sy && mouseY < sy + SLOT_SIZE;
        if (hovered) {
            gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_SPRITE,
                    sx - 4, sy - 4, 24, 24);
        }
        gfx.fakeItem(stack, sx, sy);
        gfx.itemDecorations(mc.font, stack, sx, sy);
        if (hovered) {
            gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_SPRITE,
                    sx - 4, sy - 4, 24, 24);
            gfx.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
        }
    }

    // ── Input handlers ────────────────────────────────────────────────────────

    private void drawScrollbar(GuiGraphicsExtractor gfx) {
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE,
                scrollbarTrackRect.x, scrollbarTrackRect.y,
                scrollbarTrackRect.width, scrollbarTrackRect.height);

        float max = maxScroll();
        if (max <= 0) return;

        float ratio = (float) innerScrollPanelHeight / Math.max(lastRenderedContentH, 1);
        int knobH = Math.max(SCROLL_KNOB_MIN_H, (int) (scrollbarTrackRect.height * ratio));
        int knobY = scrollbarTrackRect.y + (int) ((scroll / max) * (scrollbarTrackRect.height - knobH));
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE,
                scrollbarTrackRect.x, knobY, scrollbarTrackRect.width, knobH);
    }

    private boolean handleSearchFieldClick(MouseButtonEvent event, boolean doubleClick) {
        if (searchField == null) return false;
        if (searchField.mouseClicked(event, doubleClick)) {
            searchField.setFocused(true);
            if (screen instanceof ContainerEventHandler handler) {
                handler.setFocused(searchField);
            }
            return true;
        }
        searchField.setFocused(false);
        if (screen instanceof ContainerEventHandler handler && handler.getFocused() == searchField) {
            handler.setFocused(null);
        }
        return false;
    }

    private boolean handleScrollbarClick(MouseButtonEvent event) {
        if (!scrollbarTrackRect.contains(event.x(), event.y())) return false;
        knobGrabbed = true;
        scroll = scrollForKnobY(event.y());
        return true;
    }

    private boolean handlePageCardClick(MouseButtonEvent event) {
        if (!scrollPanelRect.contains(event.x(), event.y())) return false;
        StoragePage clicked = pageAt((int) event.x(), (int) event.y());
        if (clicked != null && !clicked.equals(activePage)) {
            clicked.navigateTo();
            return true;
        }
        return false;
    }

    private boolean handleNavPanelClick(MouseButtonEvent event) {
        if (event.x() < navPanelX || event.x() >= navPanelX + NAV_PANEL_W
                || event.y() < navPanelY || event.y() >= navPanelY + NAV_PANEL_H) {
            return false;
        }

        NavPanelCoords c = computeNavPanelCoords();

        for (int i = 0; i < StoragePage.ENDER_CHEST_COUNT; i++) {
            int sx = c.slotStartX() + i * SLOT_SIZE;
            if (event.x() >= sx && event.x() < sx + SLOT_SIZE
                    && event.y() >= c.ecRowY() && event.y() < c.ecRowY() + SLOT_SIZE) {
                StoragePage.ofEnderChest(i + 1).navigateTo();
                return true;
            }
        }

        for (int i = 0; i < StoragePage.BACKPACK_COUNT; i++) {
            int sx = c.slotStartX() + (i % 9) * SLOT_SIZE;
            int sy = (i / 9 == 0) ? c.bp1RowY() : c.bp2RowY();
            if (event.x() >= sx && event.x() < sx + SLOT_SIZE
                    && event.y() >= sy && event.y() < sy + SLOT_SIZE) {
                StoragePage.ofBackpack(i + 1).navigateTo();
                return true;
            }
        }

        return false;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private float scrollForKnobY(double mouseY) {
        return clampScroll((float) ((mouseY - scrollbarTrackRect.y) / scrollbarTrackRect.height) * maxScroll());
    }

    private void initSearchField() {
        if (searchField == null) {
            searchField = new EditBox(mc.font, 0, 0, 160, 12, Component.literal("Search items..."));
            searchField.setMaxLength(64);
            searchField.setResponder(this::onSearchChanged);
            searchField.setBordered(true);
        }
        int searchW = Math.max(80, STORAGE_INV_W - 80 - 16);
        searchField.setX(invPanelX + STORAGE_INV_W - searchW - 8);
        searchField.setY(invPanelY + 1);
        searchField.setWidth(searchW);
    }

    private void onSearchChanged(String query) {
        searchQuery = query != null ? query : "";
        scroll = 0f;
    }

    private Set<StoragePage> getFilteredPages() {
        var inventories = StorageData.INSTANCE.getInventories();
        if (searchQuery.isBlank() && EnhancedStorageConfig.showEmptyPages) {
            return inventories.keySet();
        }
        Set<StoragePage> result = new TreeSet<>();
        for (var entry : inventories.entrySet()) {
            if (includeInView(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Returns true if this inventory entry should appear in the scroll panel.
     * Empty pages are excluded during active searches regardless of {@code showEmptyPages}.
     */
    private boolean includeInView(StorageData.StorageInventory inv) {
        boolean hasData = inv != null && inv.inventory() != null;
        if (!hasData) {
            return EnhancedStorageConfig.showEmptyPages && searchQuery.isBlank();
        }
        if (searchQuery.isBlank()) return true;
        for (ItemStack stack : inv.inventory().stacks()) {
            if (matchesSearch(stack, searchQuery)) return true;
        }
        return false;
    }

    private boolean matchesSearch(ItemStack stack, String query) {
        if (stack.isEmpty()) return false;
        for (String word : query.toLowerCase().split("\\s+")) {
            if (!stackContainsWord(stack, word)) return false;
        }
        return true;
    }

    // ── Hit testing ───────────────────────────────────────────────────────────

    private boolean stackContainsWord(ItemStack stack, String word) {
        if (stack.getHoverName().getString().toLowerCase().contains(word)) return true;
        for (Component line : stack.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                mc.player, TooltipFlag.Default.NORMAL)) {
            if (line.getString().toLowerCase().contains(word)) return true;
        }
        return false;
    }

    private Rect findPageRect(StoragePage target) {
        if (target == null) return null;
        Rect[] result = {null};
        forEachPage(getFilteredPages(), (rect, page, inv) -> {
            if (result[0] == null && page.equals(target)) result[0] = rect;
        });
        return result[0];
    }

    private StoragePage pageAt(int screenX, int screenY) {
        StoragePage[] result = {null};
        forEachPage(getFilteredPages(), (rect, page, inv) -> {
            if (result[0] == null && rect.contains(screenX, screenY + (int) scroll)) result[0] = page;
        });
        return result[0];
    }

    // ── Page iteration ────────────────────────────────────────────────────────

    private boolean isSlotHovered(int slotX, int slotY, int mouseX, int contentMouseY) {
        int screenSlotY = slotY - (int) scroll;
        return screenSlotY >= scrollPanelRect.y
                && screenSlotY + SLOT_SIZE <= scrollPanelRect.y + scrollPanelRect.height
                && slotX >= scrollPanelRect.x
                && slotX + SLOT_SIZE <= scrollPanelRect.x + scrollPanelRect.width
                && mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                && contentMouseY >= slotY && contentMouseY < slotY + SLOT_SIZE;
    }

    /**
     * Iterates all visible pages in order, computing layout rects and invoking the consumer.
     * Also updates {@link #lastRenderedContentH} as a side effect for scrollbar calculations.
     */
    private void forEachPage(Set<StoragePage> filter, PageConsumer consumer) {
        int col = 0, maxRowH = 0, totalH = 0;
        for (var entry : StorageData.INSTANCE.getInventories().entrySet()) {
            if (!filter.contains(entry.getKey())) continue;
            StorageData.StorageInventory inv = entry.getValue();

            int rows = pageRows(entry.getKey(), inv, entry.getKey().equals(activePage));
            int pageH = pageCardHeight(rows, inv != null && inv.inventory() != null);
            maxRowH = Math.max(maxRowH, pageH);

            consumer.accept(
                    new Rect(overviewX + PADDING + (PAGE_WIDTH + PADDING) * col,
                            PANEL_TOP + PADDING + totalH, PAGE_WIDTH, pageH),
                    entry.getKey(), inv);

            if (++col >= pageWidthCount) {
                totalH += maxRowH + PADDING;
                col = 0;
                maxRowH = 0;
            }
        }
        lastRenderedContentH = totalH + maxRowH;
    }

    private int pageRows(StoragePage page, StorageData.StorageInventory inv, boolean isActive) {
        if (isActive) return Math.max(1, (screen.getMenu().slots.size() - 36) / 9 - NAV_ROW_COUNT);
        if (inv != null && inv.inventory() != null) return Math.max(1, inv.inventory().rows() - NAV_ROW_COUNT);
        return 0;
    }

    private int pageCardHeight(int rows, boolean hasInventory) {
        if (!hasInventory) return mc.font.lineHeight + 24;
        return rows * SLOT_SIZE + mc.font.lineHeight + 10;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void ensureAllPagesRegistered() {
        for (int i = 0; i < StoragePage.COUNT; i++) {
            StoragePage p = new StoragePage(i);
            if (!StorageData.INSTANCE.hasInventory(p)) {
                StorageData.INSTANCE.updateInventory(p, p.defaultName(), null);
            }
        }
    }

    private int[] capturePlayerSlotRelY() {
        List<Slot> slots = getPlayerSlots();
        int[] relY = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) relY[i] = slots.get(i).y;
        return relY;
    }

    private List<Slot> getPlayerSlots() {
        if (mc.player == null) return List.of();
        var playerInv = mc.player.getInventory();
        return screen.getMenu().slots.stream()
                .filter(s -> s.container == playerInv)
                .toList();
    }

    private ItemStack getRepresentativeStack(StoragePage page) {
        StorageData.StorageInventory inv = StorageData.INSTANCE.getInventory(page);
        if (inv == null) return ItemStack.EMPTY;
        if (inv.icon() != null && !inv.icon().isEmpty()) return inv.icon();
        if (inv.inventory() == null) return ItemStack.EMPTY;
        // Skip Hypixel's navigation row when scanning for a representative item.
        for (int i = NAV_ROW_SKIP_SLOTS; i < inv.inventory().stacks().size(); i++) {
            ItemStack s = inv.inventory().stacks().get(i);
            if (!s.isEmpty()) return s;
        }
        return ItemStack.EMPTY;
    }

    private NavPanelCoords computeNavPanelCoords() {
        return new NavPanelCoords(
                navPanelX + NAV_PANEL_SLOT_X,
                navPanelY + NAV_PANEL_EC_ROW_Y,
                navPanelY + NAV_PANEL_BP_ROW1_Y,
                navPanelY + NAV_PANEL_BP_ROW2_Y);
    }

    /**
     * Parses a config hex color string and caches the result between frames.
     */
    private int getSearchHighlightColor() {
        String hex = EnhancedStorageConfig.searchHighlightColor;
        if (!hex.equals(cachedHighlightHex)) {
            cachedHighlightColor = parseColor(hex);
            cachedHighlightHex = hex;
        }
        return cachedHighlightColor;
    }

    private float maxScroll() {
        return Math.max(0f, lastRenderedContentH - innerScrollPanelHeight);
    }

    private float clampScroll(float v) {
        return Mth.clamp(v, 0f, maxScroll());
    }

    @FunctionalInterface
    private interface PageConsumer {
        void accept(Rect rect, StoragePage page, StorageData.StorageInventory inv);
    }

    /**
     * Screen-space coordinates for each row of the navigation panel.
     */
    private record NavPanelCoords(int slotStartX, int ecRowY, int bp1RowY, int bp2RowY) {
    }
}
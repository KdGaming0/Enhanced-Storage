package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.mixin.AbstractContainerScreenAccessor;
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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Custom storage overlay rendered on top of vanilla {@link AbstractContainerScreen}.
 * Replaces the chest background with a scrollable dashboard of all known storage pages.
 */
public class StorageOverlay {

    // ── Sprite identifiers ────────────────────────────────────────────────────
    private static final Identifier MAIN_PANEL_SPRITE =
            Identifier.fromNamespaceAndPath("enhanced_storage", "main_panel");
    private static final Identifier PAGE_CARD_IDLE_SPRITE =
            Identifier.fromNamespaceAndPath("enhanced_storage", "page_card_idle");
    private static final Identifier PAGE_CARD_ACTIVE_SPRITE =
            Identifier.fromNamespaceAndPath("enhanced_storage", "page_card_active");
    private static final Identifier STORAGE_INVENTORY_SPRITE =
            Identifier.fromNamespaceAndPath("enhanced_storage", "storage_inventory");
    private static final Identifier STORAGE_SLOT_SPRITE =
            Identifier.fromNamespaceAndPath("enhanced_storage", "storage_slot");
    private static final Identifier SLOT_HIGHLIGHT_BACK_SPRITE =
            Identifier.fromNamespaceAndPath("enhanced_storage", "storage_slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT_SPRITE =
            Identifier.fromNamespaceAndPath("enhanced_storage", "storage_slot_highlight_front");
    private static final Identifier SCROLLER_SPRITE =
            Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("widget/scroller_background");
    private static final Identifier STORAGE_OVERVIEW_SPRITE =
            Identifier.fromNamespaceAndPath("enhanced_storage", "storage_overview");

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int SLOT_SIZE           = 18;
    private static final int PAGE_WIDTH          = SLOT_SIZE * 9 + 6;
    private static final int PADDING             = 8;
    private static final int SCROLL_BAR_W        = 6;
    private static final int SCROLL_KNOB_MIN_H   = 32;
    private static final int MIN_OVERLAY_H       = 60;
    private static final int OVERVIEW_TOP        = SLOT_SIZE;
    private static final int INV_SLOTS_TOP       = 15;
    private static final int STORAGE_INV_W       = 176;
    private static final int STORAGE_INV_H       = 97;

    /** Hypixel puts navigation buttons in the first row of every storage page — skip it. */
    private static final int SKIP_ROWS  = 1;
    private static final int SKIP_SLOTS = SKIP_ROWS * 9;

    private static final int BOTTOM_PADDING = 20;

    private static final int OVERVIEW_TEXTURE_W = 176;
    private static final int OVERVIEW_TEXTURE_H = 85;
    private static final int OVERVIEW_SLOT_START_X = 8;
    private static final int OVERVIEW_EC_ROW_Y = 15;
    private static final int OVERVIEW_BP_ROW1_Y = 43;
    private static final int OVERVIEW_BP_ROW2_Y = 61;


    // ── Colours — text only ───────────────────────────────────────────────────
    private static final int COL_TITLE_ACTIVE = 0xFF7AB4FF;
    private static final int COL_TITLE_IDLE   = 0xFFA0AABB;
    private static final int COL_PLACEHOLDER  = 0xFF505868;

    // ── References ────────────────────────────────────────────────────────────
    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerScreenAccessor accessor;
    private final StoragePage activePage;
    private final Minecraft mc;

    private int baseTopPos;
    private int[] originalPlayerSlotRelY;
    private int playerPush;

    // ── Layout state ──────────────────────────────────────────────────────────
    private int pageWidthCount;
    private int overviewX, overviewWidth, overviewHeight;
    private int innerScrollPanelWidth, innerScrollPanelHeight;
    private int invPanelX, invPanelY, invPanelW, invPanelH;
    private final boolean isOverview;
    private int overviewTextureX, overviewTextureY;

    // ── Scroll state ──────────────────────────────────────────────────────────
    private float scroll;
    private int lastRenderedContentH;
    private boolean knobGrabbed;

    // Persistent UI state across overlay instances
    private static float lastScroll = 0f;
    private static String lastSearchQuery = "";

    // ── Search state ──────────────────────────────────────────────────────────
    private EditBox searchField;
    private String searchQuery = "";
    private String cachedSearchQuery;
    private Set<StoragePage> cachedFilteredPages = Set.of();

    // ─────────────────────────────────────────────────────────────────────────

    public StorageOverlay(AbstractContainerScreen<?> screen, StoragePage activePage) {
        this.screen     = screen;
        this.accessor   = (AbstractContainerScreenAccessor) screen;
        this.activePage = activePage;
        this.mc         = Minecraft.getInstance();
        this.scroll     = lastScroll;
        this.searchQuery = lastSearchQuery;
        this.isOverview = activePage == null;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void onInit(int screenWidth, int screenHeight) {
        ensureAllPagesRegistered();
        baseTopPos             = accessor.es$getTopPos();
        originalPlayerSlotRelY = capturePlayerSlotRelY();
        recalculateMeasurements();
        scroll = clampScroll(scroll);
        initSearchField();
        computeOverviewLayout();
    }

    /**
     * Repositions chest slots onto the overlay and pushes player slots into the
     * inventory section. Vanilla renders items at (leftPos+slot.x, topPos+slot.y),
     * so adjusting slot.y is sufficient — no topPos mutation required.
     */
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
        drawOverviewTexture(gfx, mouseX, mouseY);
    }

    public List<Rect> getBounds() {
        return List.of(
                new Rect(overviewX, OVERVIEW_TOP, overviewWidth, overviewHeight),
                new Rect(overviewTextureX, overviewTextureY, OVERVIEW_TEXTURE_W, OVERVIEW_TEXTURE_H),
                new Rect(invPanelX, invPanelY, STORAGE_INV_W, STORAGE_INV_H));
    }

    public EditBox getSearchField() { return searchField; }

    public void saveState() {
        lastScroll = scroll;
        lastSearchQuery = searchQuery;
    }

    public static void clearState() {
        lastScroll = 0f;
        lastSearchQuery = "";
    }

    // ── Input delegation ──────────────────────────────────────────────────────

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (handleSearchFieldClick(event, doubleClick)) return true;
        if (handleScrollbarClick(event)) return true;
        if (!isOverview && handleOverviewNavigationClick(event)) return true;
        return handlePageClick(event);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (knobGrabbed) { knobGrabbed = false; return true; }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!knobGrabbed) return false;
        scroll = scrollForKnobY(event.y());
        return true;
    }

    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!getScrollPanel().contains(x, y)) return false;
        float dir = EnhancedStorageConfig.inverseScroll ? 1f : -1f;
        scroll = clampScroll(scroll + (float) (scrollY * EnhancedStorageConfig.scrollSpeed * dir));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (searchField == null || !searchField.isFocused()) return false;
        if (searchField.keyPressed(event)) return true;
        // Consume all remaining keys while the field is focused so vanilla hotkeys
        // (e.g. the inventory key "E") don't fire. Escape is exempt so the screen
        // can still be closed normally.
        return event.key() != GLFW.GLFW_KEY_ESCAPE;
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void recalculateMeasurements() {
        pageWidthCount = Math.clamp(
                (screen.width - PADDING * 2) / (PAGE_WIDTH + PADDING),
                1, EnhancedStorageConfig.overlayColumns);

        innerScrollPanelWidth = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING;
        overviewWidth         = innerScrollPanelWidth + SCROLL_BAR_W + PADDING * 3;
        overviewX             = screen.width / 2 - overviewWidth / 2;

        invPanelX = accessor.es$getLeftPos();
        invPanelW = accessor.es$getImageWidth();

        invPanelH = STORAGE_INV_H;
        invPanelY = screen.height - BOTTOM_PADDING - invPanelH;

        overviewHeight         = Math.max(MIN_OVERLAY_H, invPanelY - OVERVIEW_TOP);
        innerScrollPanelHeight = overviewHeight - PADDING * 2;

        int targetFirstSlotScreenY  = invPanelY + INV_SLOTS_TOP;
        int vanillaFirstSlotScreenY = baseTopPos + originalPlayerSlotRelY[0];
        playerPush = targetFirstSlotScreenY - vanillaFirstSlotScreenY;
    }

    private void computeOverviewLayout() {
        overviewTextureX = invPanelX - OVERVIEW_TEXTURE_W;
        overviewTextureY = invPanelY;
        if (overviewTextureX < 8) {
            overviewTextureX = (screen.width - OVERVIEW_TEXTURE_W) / 2;
            overviewTextureY = invPanelY - OVERVIEW_TEXTURE_H;
        }
    }

    private Rect getScrollPanel() {
        return new Rect(overviewX + PADDING, OVERVIEW_TOP + PADDING,
                innerScrollPanelWidth, innerScrollPanelHeight);
    }

    private Rect getScrollbarTrack() {
        return new Rect(overviewX + overviewWidth - PADDING - SCROLL_BAR_W,
                OVERVIEW_TOP + PADDING, SCROLL_BAR_W, innerScrollPanelHeight);
    }

    // ── Slot repositioning ────────────────────────────────────────────────────

    private void pushPlayerSlots() {
        List<Slot> playerSlots = getPlayerSlots();
        for (int i = 0; i < Math.min(playerSlots.size(), originalPlayerSlotRelY.length); i++) {
            ((SlotAccessor) playerSlots.get(i)).es$setY(originalPlayerSlotRelY[i] + playerPush);
        }
    }

    private void repositionChestSlots() {
        Rect panel      = getScrollPanel();
        Rect activeRect = findPageRect(activePage);
        int leftPos     = accessor.es$getLeftPos();

        for (Slot slot : screen.getMenu().slots) {
            assert mc.player != null;
            if (slot.container == mc.player.getInventory()) continue;

            if (activePage == null || activeRect == null || slot.index < SKIP_SLOTS) {
                hideSlot(slot);
                continue;
            }

            int visIndex = slot.index - SKIP_SLOTS;
            int screenX = activeRect.x + 4 + (visIndex % 9) * SLOT_SIZE;
            int screenY = activeRect.y + mc.font.lineHeight + 7 + (visIndex / 9) * SLOT_SIZE - (int) scroll;
            boolean inPanel = screenY >= panel.y && screenY + SLOT_SIZE <= panel.y + panel.height;

            if (inPanel) {
                ((SlotAccessor) slot).es$setX(screenX - leftPos);
                ((SlotAccessor) slot).es$setY(screenY - baseTopPos);
            } else {
                hideSlot(slot);
            }
        }
    }

    private static void hideSlot(Slot slot) {
        ((SlotAccessor) slot).es$setX(-9999);
        ((SlotAccessor) slot).es$setY(-9999);
    }

    private void repositionOverviewSlots() {
        int leftPos = accessor.es$getLeftPos();
        int slotStartX = overviewTextureX + OVERVIEW_SLOT_START_X;
        int ecRowY = overviewTextureY + OVERVIEW_EC_ROW_Y;
        int bp1RowY = overviewTextureY + OVERVIEW_BP_ROW1_Y;
        int bp2RowY = overviewTextureY + OVERVIEW_BP_ROW2_Y;

        for (Slot slot : screen.getMenu().slots) {
            assert mc.player != null;
            if (slot.container == mc.player.getInventory()) continue;

            if (slot.index >= 9 && slot.index <= 17) {
                ((SlotAccessor) slot).es$setX(slotStartX + (slot.index - 9) * SLOT_SIZE - leftPos);
                ((SlotAccessor) slot).es$setY(ecRowY - baseTopPos);
            } else if (slot.index >= 27 && slot.index <= 35) {
                ((SlotAccessor) slot).es$setX(slotStartX + (slot.index - 27) * SLOT_SIZE - leftPos);
                ((SlotAccessor) slot).es$setY(bp1RowY - baseTopPos);
            } else if (slot.index >= 36 && slot.index <= 44) {
                ((SlotAccessor) slot).es$setX(slotStartX + (slot.index - 36) * SLOT_SIZE - leftPos);
                ((SlotAccessor) slot).es$setY(bp2RowY - baseTopPos);
            } else {
                hideSlot(slot);
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void drawMainPanel(GuiGraphicsExtractor gfx) {
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, MAIN_PANEL_SPRITE,
                overviewX, OVERVIEW_TOP, overviewWidth, overviewHeight);

        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, STORAGE_INVENTORY_SPRITE,
                invPanelX, invPanelY, STORAGE_INV_W, STORAGE_INV_H);
    }

    private void drawScrollableContent(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        Rect panel = getScrollPanel();
        gfx.enableScissor(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height);
        gfx.pose().pushMatrix();
        gfx.pose().translate(0f, -scroll);

        forEachPage(getFilteredPages(), (rect, page, inv) ->
                drawPage(gfx, rect, page, inv, page.equals(activePage), mouseX, mouseY, panel));

        gfx.pose().popMatrix();
        gfx.disableScissor();
    }

    private void drawPage(GuiGraphicsExtractor gfx, Rect rect, StoragePage page,
                          StorageData.StorageInventory inv, boolean isActive,
                          int mouseX, int mouseY, Rect panel) {
        int rows = pageRows(page, inv, isActive);
        boolean hasInv = inv != null && inv.inventory() != null;
        int cardH = pageCardHeight(rows, hasInv);

        Identifier cardSprite = isActive ? PAGE_CARD_ACTIVE_SPRITE : PAGE_CARD_IDLE_SPRITE;
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, cardSprite, rect.x, rect.y, PAGE_WIDTH, cardH);

        String title = inv != null && inv.title() != null ? inv.title() : page.defaultName();
        gfx.text(mc.font, title, rect.x + 6, rect.y + 5,
                isActive ? COL_TITLE_ACTIVE : COL_TITLE_IDLE, true);

        if (!hasInv) {
            gfx.centeredText(mc.font, "Open Page",
                    rect.x + PAGE_WIDTH / 2, rect.y + cardH / 2, COL_PLACEHOLDER);
            return;
        }

        drawSlotBackgrounds(gfx, rect, rows);

        if (!isActive) {
            drawFakeItems(gfx, rect, inv.inventory().stacks(), rows, panel, mouseX, mouseY);
        }
    }

    private void drawSlotBackgrounds(GuiGraphicsExtractor gfx, Rect pageRect, int rows) {
        for (int i = 0; i < rows * 9; i++) {
            int x = pageRect.x + 3 + (i % 9) * SLOT_SIZE;
            int y = pageRect.y + mc.font.lineHeight + 6 + (i / 9) * SLOT_SIZE;
            gfx.blitSprite(RenderPipelines.GUI_TEXTURED, STORAGE_SLOT_SPRITE, x, y, SLOT_SIZE, SLOT_SIZE);
        }
    }

    private void drawFakeItems(GuiGraphicsExtractor gfx, Rect pageRect, List<ItemStack> stacks,
                               int rows, Rect panel, int mouseX, int mouseY) {
        int startIdx      = SKIP_SLOTS;
        int endIdx        = Math.min(stacks.size(), startIdx + rows * 9);
        int contentMouseY = mouseY + (int) scroll;

        for (int i = startIdx; i < endIdx; i++) {
            ItemStack stack = stacks.get(i);

            int visIndex = i - startIdx;
            int slotX = pageRect.x + 4 + (visIndex % 9) * SLOT_SIZE;
            int slotY = pageRect.y + mc.font.lineHeight + 7 + (visIndex / 9) * SLOT_SIZE;

            boolean hovered = isSlotHovered(slotX, slotY, mouseX, contentMouseY, panel);

            if (hovered) {
                gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_SPRITE,
                        slotX - 4, slotY - 4, 24, 24);
            }

            if (!searchQuery.isBlank()) {
                if (matchesSearch(stack, searchQuery)) {
                    gfx.fill(slotX, slotY, slotX + SLOT_SIZE - 2, slotY + SLOT_SIZE - 2,
                            parseColor(EnhancedStorageConfig.searchHighlightColor));
                } else {
                    gfx.fill(slotX, slotY, slotX + SLOT_SIZE - 2, slotY + SLOT_SIZE - 2, 0x88111111);
                }
            }

            if (stack.isEmpty()) continue;

            gfx.fakeItem(stack, slotX, slotY);
            gfx.itemDecorations(mc.font, stack, slotX, slotY);

            if (hovered) {
                gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_SPRITE,
                        slotX - 4, slotY - 4, 24, 24);
                gfx.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
            }
        }
    }

    private void drawInventoryLabel(GuiGraphicsExtractor gfx) {
        gfx.text(mc.font, Component.translatable("container.inventory"),
                invPanelX + 8, invPanelY + 4, COL_TITLE_IDLE, false);
    }

    private void drawOverviewTexture(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, STORAGE_OVERVIEW_SPRITE,
                overviewTextureX, overviewTextureY, OVERVIEW_TEXTURE_W, OVERVIEW_TEXTURE_H);

        gfx.text(mc.font, "Ender Chest",
                overviewTextureX + 8,
                overviewTextureY + 4,
                COL_TITLE_ACTIVE, false);
        gfx.text(mc.font, "Backpacks",
                overviewTextureX + 8,
                overviewTextureY + 33,
                COL_TITLE_ACTIVE, false);

        if (!isOverview) {
            drawOverviewFakeItems(gfx, mouseX, mouseY);
        }
    }

    private void drawOverviewFakeItems(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int slotStartX = overviewTextureX + OVERVIEW_SLOT_START_X;
        int ecRowY = overviewTextureY + OVERVIEW_EC_ROW_Y;
        int bp1RowY = overviewTextureY + OVERVIEW_BP_ROW1_Y;
        int bp2RowY = overviewTextureY + OVERVIEW_BP_ROW2_Y;

        for (int i = 0; i < StoragePage.ENDER_CHEST_COUNT; i++) {
            StoragePage page = StoragePage.ofEnderChest(i + 1);
            ItemStack stack = getRepresentativeStack(page);
            int sx = slotStartX + i * SLOT_SIZE;
            if (!stack.isEmpty()) {
                boolean hovered = mouseX >= sx && mouseX < sx + SLOT_SIZE
                        && mouseY >= ecRowY && mouseY < ecRowY + SLOT_SIZE;
                if (hovered) {
                    gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_SPRITE,
                            sx - 4, ecRowY - 4, 24, 24);
                }
                gfx.fakeItem(stack, sx, ecRowY);
                gfx.itemDecorations(mc.font, stack, sx, ecRowY);
                if (hovered) {
                    gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_SPRITE,
                            sx - 4, ecRowY - 4, 24, 24);
                    gfx.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
                }
            }
        }

        for (int i = 0; i < StoragePage.BACKPACK_COUNT; i++) {
            StoragePage page = StoragePage.ofBackpack(i + 1);
            ItemStack stack = getRepresentativeStack(page);
            int col = i % 9;
            int row = i / 9;
            int sx = slotStartX + col * SLOT_SIZE;
            int sy = (row == 0) ? bp1RowY : bp2RowY;
            if (!stack.isEmpty()) {
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
        }
    }

    private ItemStack getRepresentativeStack(StoragePage page) {
        StorageData.StorageInventory inv = StorageData.INSTANCE.getInventory(page);
        if (inv == null) return ItemStack.EMPTY;
        // Prefer the dedicated overview icon (saved from the Storage hub menu)
        if (inv.icon() != null && !inv.icon().isEmpty()) return inv.icon();
        if (inv.inventory() == null) return ItemStack.EMPTY;
        java.util.List<ItemStack> stacks = inv.inventory().stacks();
        // Skip Hypixel's navigation row (barrier blocks, arrows, etc.)
        for (int i = SKIP_SLOTS; i < stacks.size(); i++) {
            if (!stacks.get(i).isEmpty()) return stacks.get(i);
        }
        return ItemStack.EMPTY;
    }

    private void drawScrollbar(GuiGraphicsExtractor gfx) {
        Rect track = getScrollbarTrack();
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE,
                track.x, track.y, track.width, track.height);

        float max = maxScroll();
        if (max <= 0) return;

        float ratio = (float) innerScrollPanelHeight / Math.max(lastRenderedContentH, 1);
        int knobH   = Math.max(SCROLL_KNOB_MIN_H, (int) (track.height * ratio));
        int knobY   = track.y + (int) ((scroll / max) * (track.height - knobH));
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE,
                track.x, knobY, track.width, knobH);
    }

    // ── Hover guard ───────────────────────────────────────────────────────────

    private boolean isSlotHovered(int slotX, int slotY, int mouseX, int contentMouseY, Rect panel) {
        int screenSlotY = slotY - (int) scroll;
        boolean inViewport = screenSlotY >= panel.y
                && screenSlotY + SLOT_SIZE <= panel.y + panel.height
                && slotX >= panel.x
                && slotX + SLOT_SIZE <= panel.x + panel.width;
        return inViewport
                && mouseX        >= slotX && mouseX        < slotX + SLOT_SIZE
                && contentMouseY >= slotY && contentMouseY < slotY + SLOT_SIZE;
    }

    // ── Page iteration ────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface PageConsumer {
        void accept(Rect rect, StoragePage page, StorageData.StorageInventory inv);
    }

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
                            OVERVIEW_TOP + PADDING + totalH, PAGE_WIDTH, pageH),
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
        if (isActive) return Math.max(1, (screen.getMenu().slots.size() - 36) / 9 - SKIP_ROWS);
        if (inv != null && inv.inventory() != null) return Math.max(1, inv.inventory().rows() - SKIP_ROWS);
        return 0;
    }

    private int pageCardHeight(int rows, boolean hasInventory) {
        if (!hasInventory) return mc.font.lineHeight + 24;
        return rows * SLOT_SIZE + mc.font.lineHeight + 10;
    }

    // ── Hit-testing ───────────────────────────────────────────────────────────

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

    // ── Input handlers ────────────────────────────────────────────────────────

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
        if (!getScrollbarTrack().contains(event.x(), event.y())) return false;
        knobGrabbed = true;
        scroll = scrollForKnobY(event.y());
        return true;
    }

    private boolean handlePageClick(MouseButtonEvent event) {
        if (!getScrollPanel().contains(event.x(), event.y())) return false;
        StoragePage clicked = pageAt((int) event.x(), (int) event.y());
        if (clicked != null && !clicked.equals(activePage)) {
            clicked.navigateTo();
            return true;
        }
        return false;
    }

    private boolean handleOverviewNavigationClick(MouseButtonEvent event) {
        if (event.x() < overviewTextureX || event.x() >= overviewTextureX + OVERVIEW_TEXTURE_W
                || event.y() < overviewTextureY || event.y() >= overviewTextureY + OVERVIEW_TEXTURE_H) {
            return false;
        }

        int slotStartX = overviewTextureX + OVERVIEW_SLOT_START_X;
        int ecRowY = overviewTextureY + OVERVIEW_EC_ROW_Y;
        int bp1RowY = overviewTextureY + OVERVIEW_BP_ROW1_Y;
        int bp2RowY = overviewTextureY + OVERVIEW_BP_ROW2_Y;

        for (int i = 0; i < StoragePage.ENDER_CHEST_COUNT; i++) {
            int sx = slotStartX + i * SLOT_SIZE;
            if (event.x() >= sx && event.x() < sx + SLOT_SIZE
                    && event.y() >= ecRowY && event.y() < ecRowY + SLOT_SIZE) {
                StoragePage.ofEnderChest(i + 1).navigateTo();
                return true;
            }
        }

        for (int i = 0; i < StoragePage.BACKPACK_COUNT; i++) {
            int col = i % 9;
            int row = i / 9;
            int sx = slotStartX + col * SLOT_SIZE;
            int sy = (row == 0) ? bp1RowY : bp2RowY;
            if (event.x() >= sx && event.x() < sx + SLOT_SIZE
                    && event.y() >= sy && event.y() < sy + SLOT_SIZE) {
                StoragePage.ofBackpack(i + 1).navigateTo();
                return true;
            }
        }

        return false;
    }

    private float scrollForKnobY(double mouseY) {
        Rect track = getScrollbarTrack();
        return clampScroll((float) ((mouseY - track.y) / track.height) * maxScroll());
    }

    // ── Search ────────────────────────────────────────────────────────────────

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
        searchField.setValue(searchQuery);
    }

    private void onSearchChanged(String query) {
        searchQuery       = query != null ? query : "";
        cachedSearchQuery = null;
        scroll = 0f;
    }

    private Set<StoragePage> getFilteredPages() {
        if (searchQuery.isBlank()) return StorageData.INSTANCE.getInventories().keySet();
        if (searchQuery.equals(cachedSearchQuery)) return cachedFilteredPages;

        cachedFilteredPages = StorageData.INSTANCE.getInventories().entrySet().stream()
                .filter(e -> e.getValue() == null
                        || e.getValue().inventory() == null
                        || e.getValue().inventory().stacks().stream()
                        .anyMatch(s -> matchesSearch(s, searchQuery)))
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        cachedSearchQuery = searchQuery;
        return cachedFilteredPages;
    }

    private boolean matchesSearch(ItemStack stack, String query) {
        if (stack.isEmpty()) return false;
        Set<String> words = new TreeSet<>(Arrays.asList(query.toLowerCase().split("\\s+")));
        words.removeIf(stack.getHoverName().getString().toLowerCase()::contains);
        if (words.isEmpty()) return true;
        for (Component line : stack.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                mc.player, TooltipFlag.Default.NORMAL)) {
            words.removeIf(line.getString().toLowerCase()::contains);
            if (words.isEmpty()) return true;
        }
        return false;
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
        return screen.getMenu().slots.stream()
                .filter(s -> {
                    assert mc.player != null;
                    return s.container == mc.player.getInventory();
                })
                .toList();
    }

    private float maxScroll()          { return Math.max(0f, lastRenderedContentH - innerScrollPanelHeight); }
    private float clampScroll(float v) { return Mth.clamp(v, 0f, maxScroll()); }

    private static int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) return 0xFF000000 | Integer.parseInt(hex, 16);
            if (hex.length() == 8) { int v = (int) Long.parseLong(hex, 16); return (v >> 8) | ((v & 0xFF) << 24); }
        } catch (NumberFormatException ignored) {}
        return 0xFFFFFFFF;
    }
}

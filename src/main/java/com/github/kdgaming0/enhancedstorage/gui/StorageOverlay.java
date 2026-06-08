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
import net.minecraft.network.chat.Component;
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

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int SLOT_SIZE     = 18;
    private static final int PAGE_WIDTH    = SLOT_SIZE * 9 + 4;
    private static final int PADDING       = 8;
    private static final int TOP_BAR_H     = 26;
    private static final int SCROLL_BAR_W  = 6;
    private static final int MIN_OVERLAY_H = 60;
    private static final int OVERVIEW_TOP  = SLOT_SIZE;
    private static final int INV_SLOTS_TOP = 18;

    /** Hypixel puts navigation buttons in the first row of every storage page — skip it. */
    private static final int SKIP_ROWS  = 1;
    private static final int SKIP_SLOTS = SKIP_ROWS * 9;

    private static final int BOTTOM_PADDING = 20;

    // ── Colours — Dark Glass theme ────────────────────────────────────────────
    private static final int COL_OVERLAY_BG       = 0xF0080C18;
    private static final int COL_PANEL_BORDER      = 0xFF1A3060;
    private static final int COL_PAGE_BG           = 0xFF0C1020;
    private static final int COL_PAGE_BG_ACTIVE    = 0xFF0F1A36;
    private static final int COL_SLOT_ITEM         = 0xFF131828;
    private static final int COL_SLOT_TL           = 0xFF07090F;
    private static final int COL_SLOT_BR           = 0xFF1E2840;
    private static final int COL_SLOT_HOVER        = 0x60FFFFFF;
    private static final int COL_TITLE_ACTIVE      = 0xFF7AB4FF;
    private static final int COL_TITLE_IDLE        = 0xFFA0AABB;
    private static final int COL_PLACEHOLDER       = 0xFF505868;

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

    // ── Scroll state ──────────────────────────────────────────────────────────
    private float scroll;
    private int lastRenderedContentH;
    private boolean knobGrabbed;

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
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void onInit(int screenWidth, int screenHeight) {
        ensureAllPagesRegistered();
        baseTopPos             = accessor.es$getTopPos();
        originalPlayerSlotRelY = capturePlayerSlotRelY();
        recalculateMeasurements();
        scroll = clampScroll(scroll);
        initSearchField();
    }

    /**
     * Repositions chest slots onto the overlay and pushes player slots into the
     * inventory section. Vanilla renders items at (leftPos+slot.x, topPos+slot.y),
     * so adjusting slot.y is sufficient — no topPos mutation required.
     */
    public void preRender(int mouseX, int mouseY) {
        pushPlayerSlots();
        repositionChestSlots();
    }

    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float delta) {
        drawPanel(gfx);
        if (searchField != null) {
            searchField.extractWidgetRenderState(gfx, mouseX, mouseY, delta);
        }
        drawScrollableContent(gfx, mouseX, mouseY);
        drawScrollbar(gfx);
        drawInventoryPanel(gfx);
    }

    public List<Rect> getBounds() {
        return List.of(
                new Rect(overviewX, OVERVIEW_TOP, overviewWidth, overviewHeight),
                new Rect(invPanelX - 1, invPanelY, invPanelW + 2, invPanelH + 1));
    }

    public EditBox getSearchField() { return searchField; }

    // ── Input delegation ──────────────────────────────────────────────────────

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (handleSearchFieldClick(event, doubleClick)) return true;
        if (handleScrollbarClick(event)) return true;
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

        int slotsH = originalPlayerSlotRelY[originalPlayerSlotRelY.length - 1]
                - originalPlayerSlotRelY[0] + SLOT_SIZE;
        invPanelH = INV_SLOTS_TOP + slotsH + 4;
        invPanelY = screen.height - BOTTOM_PADDING - invPanelH;

        overviewHeight         = Math.max(MIN_OVERLAY_H, invPanelY - OVERVIEW_TOP);
        innerScrollPanelHeight = overviewHeight - TOP_BAR_H - PADDING * 2;

        int targetFirstSlotScreenY  = invPanelY + INV_SLOTS_TOP;
        int vanillaFirstSlotScreenY = baseTopPos + originalPlayerSlotRelY[0];
        playerPush = targetFirstSlotScreenY - vanillaFirstSlotScreenY;
    }

    private Rect getScrollPanel() {
        return new Rect(overviewX + PADDING, OVERVIEW_TOP + TOP_BAR_H,
                innerScrollPanelWidth, innerScrollPanelHeight);
    }

    private Rect getScrollbarTrack() {
        return new Rect(overviewX + overviewWidth - PADDING - SCROLL_BAR_W,
                OVERVIEW_TOP + TOP_BAR_H, SCROLL_BAR_W, innerScrollPanelHeight);
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
            if (slot.container == mc.player.getInventory()) continue;

            if (activePage == null || activeRect == null || slot.index < SKIP_SLOTS) {
                hideSlot(slot);
                continue;
            }

            int visIndex = slot.index - SKIP_SLOTS;
            int screenX = activeRect.x + 2 + (visIndex % 9) * SLOT_SIZE;
            int screenY = activeRect.y + mc.font.lineHeight + 6 + (visIndex / 9) * SLOT_SIZE - (int) scroll;
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

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void drawPanel(GuiGraphicsExtractor gfx) {
        gfx.fill(overviewX,     OVERVIEW_TOP,     overviewX + overviewWidth,     invPanelY + 1, COL_PANEL_BORDER);
        gfx.fill(overviewX + 1, OVERVIEW_TOP + 1, overviewX + overviewWidth - 1, invPanelY,     COL_OVERLAY_BG);

        int iL = invPanelX - 1;
        int iR = invPanelX + invPanelW + 1;
        int iB = invPanelY + invPanelH + 1;
        gfx.fill(iL,     invPanelY, iR,     iB,     COL_PANEL_BORDER);
        gfx.fill(iL + 1, invPanelY, iR - 1, iB - 1, COL_OVERLAY_BG);
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
        int rows    = pageRows(page, inv, isActive);
        int cardH   = rows * SLOT_SIZE + mc.font.lineHeight + 10;
        int cardTop = rect.y + mc.font.lineHeight + 4;

        int borderColor = parseColor(isActive
                ? EnhancedStorageConfig.activePageOutlineColor
                : EnhancedStorageConfig.inactivePageBorderColor);
        int bgColor = isActive ? COL_PAGE_BG_ACTIVE : COL_PAGE_BG;
        gfx.fill(rect.x,     cardTop,     rect.x + PAGE_WIDTH,     rect.y + cardH,     borderColor);
        gfx.fill(rect.x + 1, cardTop + 1, rect.x + PAGE_WIDTH - 1, rect.y + cardH - 1, bgColor);

        String title = inv != null && inv.title() != null ? inv.title() : page.defaultName();
        gfx.text(mc.font, title, rect.x + 4, rect.y + 2,
                isActive ? COL_TITLE_ACTIVE : COL_TITLE_IDLE, true);

        if (inv == null || inv.inventory() == null) {
            gfx.centeredText(mc.font, "Not yet opened",
                    rect.x + PAGE_WIDTH / 2, rect.y + cardH / 2, COL_PLACEHOLDER);
            return;
        }

        drawSlotBackgrounds(gfx, rect, rows, isActive);

        if (!isActive) {
            drawFakeItems(gfx, rect, inv.inventory().stacks(), rows, panel, mouseX, mouseY);
        }
    }

    private void drawSlotBackgrounds(GuiGraphicsExtractor gfx, Rect pageRect, int rows, boolean vanillaRendered) {
        for (int i = 0; i < rows * 9; i++) {
            int x = pageRect.x + 2 + (i % 9) * SLOT_SIZE;
            int y = pageRect.y + mc.font.lineHeight + 6 + (i / 9) * SLOT_SIZE;
            drawSlotBackground(gfx, vanillaRendered ? x - 1 : x, vanillaRendered ? y - 1 : y);
        }
    }

    private void drawFakeItems(GuiGraphicsExtractor gfx, Rect pageRect, List<ItemStack> stacks,
                               int rows, Rect panel, int mouseX, int mouseY) {
        int startIdx      = SKIP_SLOTS;
        int endIdx        = Math.min(stacks.size(), startIdx + rows * 9);
        int contentMouseY = mouseY + (int) scroll;

        for (int i = startIdx; i < endIdx; i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;

            int visIndex = i - startIdx;
            int slotX = pageRect.x + 2 + (visIndex % 9) * SLOT_SIZE;
            int slotY = pageRect.y + mc.font.lineHeight + 6 + (visIndex / 9) * SLOT_SIZE;

            gfx.fakeItem(stack, slotX + 1, slotY + 1);
            gfx.itemDecorations(mc.font, stack, slotX + 1, slotY + 1);

            if (!searchQuery.isBlank() && matchesSearch(stack, searchQuery)) {
                gfx.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1,
                        parseColor(EnhancedStorageConfig.searchHighlightColor));
            }

            if (isSlotHovered(slotX, slotY, mouseX, contentMouseY, panel)) {
                gfx.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, COL_SLOT_HOVER);
                gfx.setTooltipForNextFrame(mc.font, stack, mouseX, mouseY);
            }
        }
    }

    private void drawInventoryPanel(GuiGraphicsExtractor gfx) {
        gfx.text(mc.font, Component.translatable("container.inventory"),
                invPanelX + PADDING, invPanelY + PADDING - 2, COL_TITLE_IDLE, false);

        int leftPos = accessor.es$getLeftPos();
        List<Slot> playerSlots = getPlayerSlots();
        for (int i = 0; i < Math.min(playerSlots.size(), originalPlayerSlotRelY.length); i++) {
            Slot slot = playerSlots.get(i);
            int screenX = leftPos + slot.x;
            int screenY = baseTopPos + slot.y;
            drawSlotBackground(gfx, screenX - 1, screenY - 1);
        }
    }

    private void drawSlotBackground(GuiGraphicsExtractor gfx, int x, int y) {
        gfx.fill(x,                 y,                 x + SLOT_SIZE,     y + 1,             COL_SLOT_TL);
        gfx.fill(x,                 y,                 x + 1,             y + SLOT_SIZE,     COL_SLOT_TL);
        gfx.fill(x,                 y + SLOT_SIZE - 1, x + SLOT_SIZE,     y + SLOT_SIZE,     COL_SLOT_BR);
        gfx.fill(x + SLOT_SIZE - 1, y,                 x + SLOT_SIZE,     y + SLOT_SIZE,     COL_SLOT_BR);
        gfx.fill(x + 1,             y + 1,             x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, COL_SLOT_ITEM);
    }

    private void drawScrollbar(GuiGraphicsExtractor gfx) {
        Rect track = getScrollbarTrack();
        gfx.fill(track.x, track.y, track.x + track.width, track.y + track.height, 0x30253560);

        float max = maxScroll();
        if (max <= 0) return;

        float ratio = (float) innerScrollPanelHeight / Math.max(lastRenderedContentH, 1);
        int knobH   = Math.max(20, (int) (track.height * ratio));
        int knobY   = track.y + (int) ((scroll / max) * (track.height - knobH));
        gfx.fill(track.x,     knobY,     track.x + track.width,     knobY + knobH,     0xC04878CC);
        gfx.fill(track.x + 1, knobY + 1, track.x + track.width - 1, knobY + knobH - 1, 0x80304E90);
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

            int pageH = pageRows(entry.getKey(), inv, entry.getKey().equals(activePage)) * SLOT_SIZE
                    + mc.font.lineHeight + 10;
            maxRowH = Math.max(maxRowH, pageH);

            consumer.accept(
                    new Rect(overviewX + PADDING + (PAGE_WIDTH + PADDING) * col,
                            OVERVIEW_TOP + TOP_BAR_H + totalH, PAGE_WIDTH, pageH),
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
        return 1;
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

    private float scrollForKnobY(double mouseY) {
        Rect track = getScrollbarTrack();
        return clampScroll((float) ((mouseY - track.y) / track.height) * maxScroll());
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void initSearchField() {
        if (searchField == null) {
            searchField = new EditBox(mc.font, 0, 0, 140, 16, Component.literal("Search items..."));
            searchField.setMaxLength(64);
            searchField.setResponder(this::onSearchChanged);
            searchField.setBordered(true);
        }
        searchField.setX(overviewX + PADDING);
        searchField.setY(OVERVIEW_TOP + 5);
        searchField.setWidth(Math.max(80, overviewWidth - SCROLL_BAR_W - PADDING * 4));
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
                .filter(s -> s.container == mc.player.getInventory())
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

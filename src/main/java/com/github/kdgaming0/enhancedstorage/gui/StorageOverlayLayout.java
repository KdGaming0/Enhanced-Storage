package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.api.screen.IScreen;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.EmptyComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.component.text.TruncatedTextComponent;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.github.kdgaming0.enhancedstorage.compat.ModWidgetSpace;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.config.LayoutSpacing;
import com.github.kdgaming0.enhancedstorage.gui.component.common.CachedPanelComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.common.IconButtonComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.common.ItemButtonComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.navigation.NavigationRowComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.navigation.NavigationTitleComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.page.*;
import com.github.kdgaming0.enhancedstorage.gui.component.section.SectionEndComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.section.SectionHeaderComponent;
import com.github.kdgaming0.enhancedstorage.gui.highlight.CopyHighlight;
import com.github.kdgaming0.enhancedstorage.gui.layout.IndexGrid;
import com.github.kdgaming0.enhancedstorage.gui.layout.PageViewport;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.gui.layout.RowFlow;
import com.github.kdgaming0.enhancedstorage.storage.data.*;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileSession;
import com.github.kdgaming0.enhancedstorage.util.ItemModels;
import com.github.kdgaming0.enhancedstorage.util.ItemSearch;
import com.github.kdgaming0.enhancedstorage.util.TextUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.MOD_ID;


public class StorageOverlayLayout {

    public static final int SLOTS_ACROSS = 9;
    public static final int SLOT_SIZE = 18;
    public static final int CARD_BORDER = 3;
    public static final int SCROLLBAR_WIDTH = 6;
    public static final int SCROLLBAR_GAP = 4;
    public static final int UNCACHED_CARD_HEIGHT = 50;
    private static final int CARD_BOTTOM = CARD_BORDER + 1;
    private static final int INVENTORY_WIDTH = 176;
    private static final int INVENTORY_HEIGHT = 96;
    private static final int OVERVIEW_WIDTH = 176;
    private static final int OVERVIEW_HEIGHT = 85;
    private static final int[] DIGIT_KEYS = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
            InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8,
            InputConstants.KEY_9};
    private static final int PANEL_TOP = 3;
    private static final long WARMUP_BUDGET_MS = 3;
    private static final int EDIT_MODE_TITLE_COLOR = 0xFFFFD24A;
    private static final List<Hint> EDIT_MODE_HINTS = List.of(
            new Hint("Click title", "Rename"),
            new Hint("Drag page", "Move it"),
            new Hint("Drag grip", "Move section"),
            new Hint("Right-click", "More options"),
            new Hint("Hover gap", "Add section"),
            new Hint("Drop on edge", "Stack pages"));
    private static final int SECTION_LINE_REACH = 3;
    private static final int SECTION_INSET = 4;
    private static final int NAV_INSET = 2;
    private static final int NAV_GROUP_GAP = 4;
    private static final int NAV_MIN_WIDTH = 70;
    private static final int NAV_MAX_WIDTH = 120;
    private static final int NAV_MIN_HEIGHT = 75;
    private static final int BUTTON_SIZE = 24;
    private static final int BUTTON_GAP = 2;
    private final boolean editMode;
    private int nextStackTag;
    private final boolean editRiftContext;
    private final List<GridCellComponent> cells = new ArrayList<>();
    private final List<GridCellComponent> visibleCells = new ArrayList<>();
    private final List<PageCardComponent> visibleCards = new ArrayList<>();
    private final List<SectionFrame> sectionFrames = new ArrayList<>();
    private final List<PlacedRow> rows = new ArrayList<>();
    private final Map<StorageKey, Boolean> pageMatchCache = new HashMap<>();
    private final Set<GridCellComponent> searchMisses = new HashSet<>();
    private ItemStack copyFilterTarget = ItemStack.EMPTY;
    private String pageMatchQuery = "";
    private int contentInset;
    private int builtModWidgetMargin;
    private boolean quickNavBottomTabSelected;
    private int frameWidth;
    private int liveRowTop = -1;
    private int liveRowBottom = -1;
    // Row-flow inputs captured by build() so applySearch() can re-pack rows without re-running build().
    private Font font;
    private int maxSlotsAcross;
    private @Nullable StorageKey liveKey;
    private int liveRows;
    private int cardWidth;
    private int rowContentWidth;
    private int titleAreaHeight;
    private boolean riftContext;
    private int pageOverviewParentX;
    private int pageOverviewParentY;
    private int mainBackgroundX;
    private int mainBackgroundY;
    private int mainBackgroundWidth;
    private int mainBackgroundHeight;
    private PageCardComponent openCard;
    private SpriteComponent inventoryPanel;
    private SpriteComponent overviewPanel;
    private PageViewport pageOverview;
    private EditBoxWidget searchBox;
    private StorageOverlayState state;
    private OverlayActions actions;
    private @Nullable Deque<ItemStack> warmupQueue;
    private @Nullable SectionHeaderComponent draggedSection;
    private int navigationWidth;
    private EnhancedStorageConfig.InventoryPosition inventoryPosition = EnhancedStorageConfig.InventoryPosition.BOTTOM;
    private boolean navigationStacked;
    private int columnX;
    private int panelTop;
    private int navigationParentX;
    private int navigationParentY;
    private @Nullable PageViewport navigationList;
    private @Nullable NavigationTitleComponent navigationTitle;
    private @Nullable Rect navigationBounds;
    private boolean searchDirty;
    private @Nullable Rect toolkitButtonBounds;
    private @Nullable Rect settingsButtonBounds;
    private @Nullable Rect themeButtonBounds;
    private @Nullable Rect editButtonBounds;
    private @Nullable Rect resetButtonBounds;
    private @Nullable Rect inventoryBounds;
    private @Nullable Rect overviewBounds;
    public StorageOverlayLayout() {
        this(false, false);
    }

    private StorageOverlayLayout(boolean editMode, boolean editRiftContext) {
        this.editMode = editMode;
        this.editRiftContext = editRiftContext;
    }

    /**
     * Edit mode has no live page to infer the context from, so it is given explicitly.
     */
    public static StorageOverlayLayout forEditMode(boolean rift) {
        return new StorageOverlayLayout(true, rift);
    }

    public static int pageWidth() {
        return EnhancedStorageConfig.pageWidth;
    }

    public static int pageWidth(StorageKey key) {
        return PageWidths.getInstance().get(key)
                .map(width -> Math.clamp(width, PageWidths.MIN_WIDTH, PageWidths.MAX_WIDTH))
                .orElse(pageWidth());
    }

    private int computeMainBackgroundHeight(int height, boolean inventoryBelow) {
        return height - computeTopMargin() - computeBottomMargin(inventoryBelow);
    }

    // Returns true if the bottom margin for the mod widget space has changed since the last build, which may require a rebuild of the overlay.
    public boolean modWidgetSpaceChanged() {
        return ModWidgetSpace.bottomMargin(LayoutSpacing.bottomMargin(), quickNavBottomTabSelected) != builtModWidgetMargin;
    }

    public void setQuickNavBottomTabSelected(boolean selected) {
        this.quickNavBottomTabSelected = selected;
    }

    public static int computeMainBackgroundY() {
        return computeTopMargin();
    }

    private static int computeTopMargin() {
        // Skyblocker's Quick Nav tabs sit just above the overlay.
        return ModWidgetSpace.topMargin(LayoutSpacing.topMargin());
    }

    private int computeBottomMargin(boolean inventoryBelow) {
        // Reserve room for the inventory panel when it sits directly below the page overview,
        // plus room below it for Quick Nav tabs and RRV's search bar.
        return ModWidgetSpace.bottomMargin(LayoutSpacing.bottomMargin(), quickNavBottomTabSelected)
                + (inventoryBelow ? INVENTORY_HEIGHT : 0);
    }

    private static List<StorageKey> applyCustomOrder(List<StorageKey> defaultOrder) {
        PageOrder order = PageOrder.getInstance();

        // Split into customs (with a requested position) and the rest (default flow).
        record Custom(StorageKey key, int pos, int defaultIndex) {
        }
        List<Custom> customs = new ArrayList<>();
        List<StorageKey> defaults = new ArrayList<>();

        for (int i = 0; i < defaultOrder.size(); i++) {
            StorageKey key = defaultOrder.get(i);
            Optional<Integer> pos = order.get(key);
            if (pos.isPresent()) customs.add(new Custom(key, pos.get(), i));
            else defaults.add(key);
        }

        // Place lower requested positions first; ties keep default flow order.
        customs.sort(Comparator.comparingInt(Custom::pos).thenComparingInt(Custom::defaultIndex));

        // Build the final list by walking target slots, inserting customs where they ask
        // and filling every other slot from the default-flow queue.
        List<StorageKey> result = new ArrayList<>();
        Deque<StorageKey> defaultQueue = new ArrayDeque<>(defaults);
        int nextCustom = 0;

        for (int slot = 1; !defaultQueue.isEmpty() || nextCustom < customs.size(); slot++) {
            // Any custom whose requested slot is this one (or earlier, if clamped low) goes here.
            if (nextCustom < customs.size() && customs.get(nextCustom).pos() <= slot) {
                result.add(customs.get(nextCustom).key());
                nextCustom++;
            } else if (!defaultQueue.isEmpty()) {
                result.add(defaultQueue.poll());
            } else {
                // Only customs left with positions beyond the current slot (e.g. "30"): append them.
                result.add(customs.get(nextCustom).key());
                nextCustom++;
            }
        }

        return result;
    }

    private static Identifier getSettingsIcon() {
        return Identifier.fromNamespaceAndPath(MOD_ID, "icons/gear_icon");
    }

    private static Identifier getEditIcon() {
        return Identifier.fromNamespaceAndPath(MOD_ID, "icons/edit_icon");
    }

    private static Identifier getResetIcon() {
        return Identifier.fromNamespaceAndPath(MOD_ID, "icons/reset_icon");
    }

    private static ItemStack buildToolkitIcon() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL,
                Identifier.fromNamespaceAndPath("hypixel_skyblock",
                        "item/island_relevant/foraging_2/hunting_toolkit"));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Toolkits").withStyle(s -> s.withItalic(false)));
        return stack;
    }

    private static int widestRow(List<RowFlow.Box> boxes, int limit) {
        int widest = 0;
        int start = 0;
        while (start < boxes.size()) {
            RowFlow.Row row = RowFlow.pack(boxes, start, boxes.size(), limit,
                    LayoutSpacing.cardSpacing(), UNCACHED_CARD_HEIGHT);
            widest = Math.max(widest, row.width());
            start = row.end();
        }
        return widest;
    }

    private static boolean anySectionOpen(List<LayoutEntry> entries) {
        return entries.stream().anyMatch(entry -> entry instanceof LayoutEntry.Section section && !section.collapsed());
    }

    private static List<LayoutEntry> closeSections(List<LayoutEntry> entries) {
        List<LayoutEntry> result = new ArrayList<>();
        boolean inSection = false;
        for (LayoutEntry entry : entries) {
            switch (entry) {
                case LayoutEntry.Section section -> {
                    if (inSection) result.add(new LayoutEntry.SectionEnd());
                    result.add(section);
                    inSection = true;
                }
                case LayoutEntry.SectionEnd end -> {
                    if (inSection) result.add(end);
                    inSection = false;
                }
                default -> result.add(entry);
            }
        }
        if (inSection) result.add(new LayoutEntry.SectionEnd());
        return result;
    }

    static int stackStart(List<GridCellComponent> order, int i) {
        int tag = order.get(i).stackTag();
        if (tag == GridCellComponent.NO_STACK) return i;
        while (i > 0 && order.get(i - 1).stackTag() == tag) i--;
        return i;
    }

    static int stackEnd(List<GridCellComponent> order, int i) {
        int tag = order.get(i).stackTag();
        i++;
        if (tag == GridCellComponent.NO_STACK) return i;
        while (i < order.size() && order.get(i).stackTag() == tag) i++;
        return i;
    }

    private static List<LayoutEntry> entriesOf(List<GridCellComponent> list) {
        List<LayoutEntry> result = new ArrayList<>();
        int i = 0;
        while (i < list.size()) {
            GridCellComponent cell = list.get(i);
            int end = stackEnd(list, i);
            if (cell instanceof PageCardComponent && end - i > 1) {
                List<StorageKey> keys = new ArrayList<>();
                for (int j = i; j < end; j++) {
                    if (list.get(j) instanceof PageCardComponent card) keys.add(card.getKey());
                }
                result.add(LayoutOps.stackOf(keys));
            } else {
                result.add(cell.entry());
            }
            i = end;
        }
        return result;
    }

    private static boolean canSplitBefore(PlacedRow row, @Nullable PlacedRow above, boolean nextToSectionsOnly) {
        if (row.first() instanceof SectionHeaderComponent) return true;
        if (row.section() != null) return false;
        return above == null || above.section() != null || !nextToSectionsOnly;
    }

    private static List<StorageKey> pageKeys(List<LayoutEntry> entries) {
        List<StorageKey> keys = new ArrayList<>();
        for (LayoutEntry entry : entries) keys.addAll(LayoutOps.keysOf(entry));
        return keys;
    }

    private static int freeSlots(StorageKey key) {
        return PageCache.getInstance().get(key).map(PageCache.CachedPage::emptySlots).orElse(0);
    }

    private void addPanelTitle(SpriteComponent panel, String text, int color) {
        addPanelText(panel, 7, 3, text, color);
    }

    private void addPanelText(SpriteComponent panel, int x, int y, String text, int color) {
        TextComponent component = new TextComponent(x, y + panelTop, Component.literal(text), color);
        component.updateParentPosition(panel.getTotalX(), panel.getTotalY(), panel.getWidth(), panel.getHeight());
        component.setDrawShadow(OverlayTheme.titleShadow());
        panel.addComponent(component);
    }

    private static Rect placeButton(IScreen screen, AbstractComponent button, int x, int y, int size,
                                    int screenWidth, int screenHeight) {
        button.updateParentPosition(0, 0, screenWidth, screenHeight);
        screen.addComponent(button);
        return new Rect(x, y, size, size);
    }

    /**
     * Headers and the end marker sit alone on their row, so they also end the row before them.
     */
    private static int nextBoundary(List<List<GridCellComponent>> groups, int from) {
        int i = from;
        while (i < groups.size() && !isBoundary(groups.get(i).getFirst())) i++;
        return i;
    }

    private static boolean endsSectionAt(List<List<GridCellComponent>> groups, int index) {
        while (index < groups.size() && isFillSpacer(groups.get(index).getFirst())) index++;
        return index == groups.size() || isBoundary(groups.get(index).getFirst());
    }

    private static boolean isFillSpacer(GridCellComponent cell) {
        return cell instanceof SpacerCardComponent spacer && spacer.isFill();
    }

    /**
     * Splits cells into flow items: the cards of one stack become one group.
     */
    private static List<List<GridCellComponent>> groupStacks(List<GridCellComponent> cells) {
        List<List<GridCellComponent>> groups = new ArrayList<>();
        int i = 0;
        while (i < cells.size()) {
            int end = stackEnd(cells, i);
            groups.add(new ArrayList<>(cells.subList(i, end)));
            i = end;
        }
        return groups;
    }

    private static RowFlow.Box boxOf(List<GridCellComponent> group, int spacing) {
        GridCellComponent first = group.getFirst();
        if (!(first instanceof PageCardComponent)) {
            return new RowFlow.Box(first.getWidth(), 0, isFillSpacer(first));
        }
        int width = 0;
        int height = -spacing;
        for (GridCellComponent cell : group) {
            width = Math.max(width, cell.getWidth());
            height += cell.getHeight() + spacing;
        }
        return new RowFlow.Box(width, height);
    }

    private static boolean isBoundary(GridCellComponent cell) {
        return cell instanceof SectionHeaderComponent || cell instanceof SectionEndComponent;
    }

    // While searching, a header with no matching pages under it is only noise.
    private static void hideEmptySections(List<GridCellComponent> list) {
        boolean pageBelow = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            GridCellComponent cell = list.get(i);
            if (cell instanceof SectionHeaderComponent) {
                if (!pageBelow) list.remove(i);
                pageBelow = false;
            } else pageBelow = !(cell instanceof SectionEndComponent);
        }
    }

    private static boolean nameMatches(String name, String query) {
        return EnhancedStorageConfig.searchPageAndSectionNames
                && TextUtils.stripString(TextUtils.colorCodes(name)).contains(TextUtils.stripString(query));
    }

    private static boolean customNameMatches(StorageKey key, String query) {
        return PageNames.getInstance().get(key).map(name -> nameMatches(name, query)).orElse(false);
    }

    private static int digitKey(int key) {
        for (int i = 0; i < DIGIT_KEYS.length; i++) {
            if (DIGIT_KEYS[i] == key) return i + 1;
        }
        return 0;
    }

    private static boolean isArrow(int key) {
        return key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN
                || key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT;
    }

    private static String emptySlotsDescription(StorageKey key) {
        return PageCache.getInstance().get(key).map(page -> {
            int empty = page.emptySlots();
            return empty == 0 ? "Full: no empty slots" : empty == 1 ? "1 empty slot" : empty + " empty slots";
        }).orElse("Not cached yet: open it once to see it here");
    }

    private int slotCount(StorageKey key) {
        if (key.equals(liveKey)) return (liveRows - 1) * SLOTS_ACROSS;
        return PageCache.getInstance().get(key).map(page -> page.items().size()).orElse(0);
    }

    private int slotsAcross(StorageKey key) {
        int chosen = Math.min(pageWidth(key), maxSlotsAcross);
        int count = slotCount(key);
        return count == 0 ? chosen : Math.clamp(count, PageWidths.MIN_WIDTH, chosen);
    }

    private int cardWidthFor(StorageKey key) {
        return CARD_BORDER * 2 + slotsAcross(key) * LayoutSpacing.slotSize();
    }

    private int cardHeightFor(StorageKey key) {
        if (!key.equals(liveKey) && PageCache.getInstance().get(key).isEmpty()) return UNCACHED_CARD_HEIGHT;
        int rows = Math.max(1, Math.ceilDiv(slotCount(key), slotsAcross(key)));
        int bottom = titleStyle() == EnhancedStorageConfig.PageTitleStyle.COMPACT ? CARD_BORDER : CARD_BOTTOM;
        return PageCardComponent.slotGridTop(titleStyle(), CARD_BORDER, font.lineHeight) + rows * LayoutSpacing.slotSize() + bottom;
    }

    private EnhancedStorageConfig.PageTitleStyle titleStyle() {
        // Edit mode keeps a title so pages stay can be renamed.
        EnhancedStorageConfig.PageTitleStyle style = LayoutSpacing.pageTitleStyle();
        return editMode && style == EnhancedStorageConfig.PageTitleStyle.HIDDEN
                ? EnhancedStorageConfig.PageTitleStyle.COMPACT
                : style;
    }

    public int getMainBackgroundX() {
        return mainBackgroundX;
    }

    public int getMainBackgroundY() {
        return mainBackgroundY;
    }

    public int getMainBackgroundWidth() {
        return mainBackgroundWidth;
    }

    public int getMainBackgroundHeight() {
        return mainBackgroundHeight;
    }

    public @Nullable PageCardComponent getOpenCard() {
        return openCard != null && visibleCards.contains(openCard) ? openCard : null;
    }

    // Both rows share one centre: Skyblocker puts every tab on the same 7-column grid, so the top tabs line up with the bottom ones.
    public int quickNavRowCenterX() {
        if (inventoryPosition == EnhancedStorageConfig.InventoryPosition.BOTTOM) {
            return inventoryPanel.getTotalX() + inventoryPanel.getWidth() / 2;
        }
        return mainBackgroundX + mainBackgroundWidth / 2;
    }

    public SpriteComponent getInventoryPanel() {
        return inventoryPanel;
    }

    public SpriteComponent getOverviewPanel() {
        return overviewPanel;
    }

    public PageViewport getPageOverview() {
        return pageOverview;
    }

    public EditBoxWidget getSearchBox() {
        return searchBox;
    }

    public void searchFor(ItemStack stack) {
        if (searchBox == null || stack.isEmpty()) return;
        String name = ItemSearch.searchTermFor(stack);
        searchBox.setValue(name);
        state.setSearchQuery(name);
        searchDirty = true;
    }

    public int getPanelContentTop() {
        return panelTop;
    }

    public @Nullable Rect getInventoryBounds() {
        return inventoryBounds;
    }

    /**
     * Null when the Storage Overview card is hidden (Rift context or disabled in config).
     */
    public @Nullable Rect getOverviewBounds() {
        return overviewBounds;
    }

    public @Nullable Rect getPageOverviewBounds() {
        if (pageOverview == null) return null;
        return new Rect(pageOverview.getX(), pageOverview.getY(),
                pageOverview.getWidth(), pageOverview.getHeight());
    }

    /**
     * Null when the navigation panel is disabled or there is no room for it.
     */
    public @Nullable Rect getNavigationBounds() {
        return navigationBounds;
    }

    public @Nullable PageViewport getNavigationList() {
        return navigationList;
    }

    /**
     * Builds the full overlay onto the given screen.
     *
     * @param liveKey  the page backed by a real menu right now, or null in browse mode
     * @param liveRows row count of the real menu (ignored when liveKey == null)
     */
    public void build(IScreen screen, Font font, int width, int height, StorageOverlayState state,
                      @Nullable StorageKey liveKey, int liveRows, OverlayActions actions) {
        resetForBuild(font, state, actions, liveKey);
        this.liveKey = liveKey;
        this.liveRows = liveRows;
        List<LayoutEntry> entries = collectEntries(liveKey);
        computeGeometry(width, height, entries);

        SpriteComponent mainBackground = buildMainPanel(screen);
        buildNavigationPanel(screen, mainBackground, entries);
        buildCells(entries, liveKey, liveRows);
        buildPageViewport(mainBackground);

        buildBottomPanels(screen, width, mainBackground);
        addOverviewIndexItems(liveKey);
        if (editMode) {
            addPanelTitle(inventoryPanel, "Edit Mode", EDIT_MODE_TITLE_COLOR);
            addEditModeHints(inventoryPanel);
        } else {
            addPanelTitle(inventoryPanel, "Inventory", OverlayTheme.titleColor());
        }
        if (overviewPanel != null) {
            boolean overviewActive = liveKey != null && liveKey.type() == StorageKey.Type.STORAGE_INDEX;
            addPanelTitle(overviewPanel, "Storage Overview", overviewActive ? 0xFFFFD24A : OverlayTheme.titleColor());
        }

        ensureWarmupQueue();
        if (!editMode) buildSearchBox(screen);
        buildButtons(screen, width, height);
    }

    private void resetForBuild(Font font, StorageOverlayState state, OverlayActions actions,
                               @Nullable StorageKey liveKey) {
        this.font = font;
        this.state = state;
        this.actions = actions;
        this.titleAreaHeight = font.lineHeight + 2;
        this.riftContext = editMode ? editRiftContext : liveKey != null && liveKey.type() == StorageKey.Type.RIFT;
        this.openCard = null;
        this.searchDirty = false;
        this.liveRowTop = -1;
        this.liveRowBottom = -1;
        this.cells.clear();
        this.visibleCells.clear();
        this.visibleCards.clear();
        this.pageMatchCache.clear();
        this.pageMatchQuery = "";
        this.toolkitButtonBounds = null;
        this.settingsButtonBounds = null;
        this.themeButtonBounds = null;
        this.editButtonBounds = null;
        this.resetButtonBounds = null;
        this.draggedSection = null;
        this.navigationList = null;
        this.navigationTitle = null;
        this.navigationBounds = null;
    }

    private void computeGeometry(int width, int height, List<LayoutEntry> entries) {
        // Frames need room around the cards, so every row moves in to keep the columns aligned.
        boolean framed = entries.stream().anyMatch(LayoutEntry.Section.class::isInstance);
        this.contentInset = framed ? SECTION_INSET : 0;
        this.mainBackgroundY = computeMainBackgroundY();

        int available = width - LayoutSpacing.horizontalMargin() - LayoutSpacing.innerPadding() * 2
                - SCROLLBAR_WIDTH - LayoutSpacing.scrollbarGap() - contentInset * 2;
        int slotSize = LayoutSpacing.slotSize();
        this.maxSlotsAcross = Math.max(PageWidths.MIN_WIDTH, (available - CARD_BORDER * 2) / slotSize);
        this.cardWidth = CARD_BORDER * 2 + Math.min(pageWidth(), maxSlotsAcross) * slotSize;

        // "Pages per row" is measured in default-width pages, so narrow pages pack more per row.
        int pages = EnhancedStorageConfig.maxPagePerRow;
        int budget = pages * cardWidth + (pages - 1) * LayoutSpacing.cardSpacing();
        // 0 = Auto: rows may use the full-screen width
        int limit = pages == 0 ? available : Math.min(budget, available);
        setRowWidth(width, entries, limit);

        this.navigationWidth = 0;
        this.navigationStacked = false;
        this.inventoryPosition = EnhancedStorageConfig.inventoryPosition;
        if (inventoryPosition != EnhancedStorageConfig.InventoryPosition.BOTTOM && !placeSideColumn(width, height, entries)) {
            this.inventoryPosition = EnhancedStorageConfig.InventoryPosition.BOTTOM;
            this.navigationWidth = 0;
            this.navigationStacked = false;
            setRowWidth(width, entries, limit);
        }
        boolean bottom = inventoryPosition == EnhancedStorageConfig.InventoryPosition.BOTTOM;
        if (bottom && EnhancedStorageConfig.navigationPanel != EnhancedStorageConfig.NavigationPanelSide.DISABLED
                && !fitBeside(width, entries, 0, 0)) {
            this.navigationWidth = 0;
            setRowWidth(width, entries, limit);
        }
        this.mainBackgroundHeight = computeMainBackgroundHeight(height, bottom);
        this.builtModWidgetMargin = ModWidgetSpace.bottomMargin(LayoutSpacing.bottomMargin(), quickNavBottomTabSelected);
        this.panelTop = bottom ? 0 : PANEL_TOP;
    }

    private void setRowWidth(int width, List<LayoutEntry> entries, int limit) {
        // Shrinks to the widest row so narrow pages don't leave empty overlay; never below one default page.
        this.rowContentWidth = Math.max(packedWidth(entries, limit), cardWidth);
        this.frameWidth = rowContentWidth + contentInset * 2;
        this.mainBackgroundWidth = frameWidth + LayoutSpacing.innerPadding() * 2 + SCROLLBAR_WIDTH + LayoutSpacing.scrollbarGap();
        this.mainBackgroundX = (width / 2) - (mainBackgroundWidth / 2);
    }

    /**
     * Width of the widest row {@link RowFlow} produces at this limit, so the measured width and the real rows always agree.
     */
    private int packedWidth(List<LayoutEntry> entries, int limit) {
        int spacing = LayoutSpacing.cardSpacing();
        int widest = 0;
        List<RowFlow.Box> segment = new ArrayList<>();
        for (LayoutEntry entry : entries) {
            switch (entry) {
                case LayoutEntry.Page(StorageKey key) ->
                        segment.add(new RowFlow.Box(cardWidthFor(key), cardHeightFor(key)));
                case LayoutEntry.Stack(List<StorageKey> keys) -> {
                    int width = 0;
                    int height = -spacing;
                    for (StorageKey key : keys) {
                        width = Math.max(width, cardWidthFor(key));
                        height += cardHeightFor(key) + spacing;
                    }
                    segment.add(new RowFlow.Box(width, height));
                }
                case LayoutEntry.Spacer spacer -> segment.add(spacerBox(spacer));
                case LayoutEntry.Section _, LayoutEntry.SectionEnd _ -> {
                    widest = Math.max(widest, widestRow(segment, limit));
                    segment.clear();
                }
            }
        }
        return Math.max(widest, widestRow(segment, limit));
    }

    private int spacerWidth(LayoutEntry.Spacer spacer) {
        return spacer.fill() ? 0 : CARD_BORDER * 2 + Math.min(spacer.slots(), maxSlotsAcross) * LayoutSpacing.slotSize();
    }

    private RowFlow.Box spacerBox(LayoutEntry.Spacer spacer) {
        return new RowFlow.Box(spacerWidth(spacer), 0, spacer.fill());
    }

    private boolean placeSideColumn(int width, int height, List<LayoutEntry> entries) {
        int mainHeight = computeMainBackgroundHeight(height, false);
        if (sideStackHeight() > mainHeight) return false;

        boolean sameSide = navigationSharesSide(inventoryPosition);
        // Too short to stack above the inventory, it becomes its own panel between the grid and the column.
        this.navigationStacked = sameSide && mainHeight - sideStackHeight() - LayoutSpacing.columnGap() >= NAV_MIN_HEIGHT;

        int column = LayoutSpacing.panelGap() + INVENTORY_WIDTH;
        boolean left = inventoryPosition == EnhancedStorageConfig.InventoryPosition.LEFT;
        if (!fitBeside(width, entries, left ? column : 0, left ? 0 : column)) return false;

        int besideNav = sameSide && !navigationStacked ? navigationWidth + LayoutSpacing.panelGap() : 0;
        this.columnX = left
                ? mainBackgroundX - besideNav - column
                : mainBackgroundX + mainBackgroundWidth + besideNav + LayoutSpacing.panelGap();
        if (navigationStacked) this.navigationWidth = INVENTORY_WIDTH;
        return true;
    }

    /**
     * Narrows the rows until everything beside the main panel fits, sizes a separate navigation panel and places the
     * main panel for the centring mode. {@code fixedLeft} and {@code fixedRight} are the panel column's share of each
     * side. Returns false when even the narrowest rows leave too little room.
     */
    private boolean fitBeside(int width, List<LayoutEntry> entries, int fixedLeft, int fixedRight) {
        EnhancedStorageConfig.NavigationPanelSide nav = EnhancedStorageConfig.navigationPanel;
        boolean separateNav = nav != EnhancedStorageConfig.NavigationPanelSide.DISABLED && !navigationStacked;
        boolean navLeft = nav == EnhancedStorageConfig.NavigationPanelSide.LEFT;
        int navMin = separateNav ? LayoutSpacing.panelGap() + NAV_MIN_WIDTH : 0;
        int left = fixedLeft + (navLeft ? navMin : 0);
        int right = fixedRight + (navLeft ? 0 : navMin);
        boolean whole = centersWholeOverlay();

        int free;
        while (true) {
            free = width - LayoutSpacing.horizontalMargin() - mainBackgroundWidth;
            int missing = whole ? left + right - free : Math.max(left, right) - free / 2;
            if (missing <= 0) break;
            int before = rowContentWidth;
            setRowWidth(width, entries, before - missing * (whole ? 1 : 2));
            if (rowContentWidth >= before) return false;
        }

        if (separateNav) {
            int spare = whole ? free - left - right : free / 2 - (navLeft ? left : right);
            this.navigationWidth = Math.min(NAV_MIN_WIDTH + spare, NAV_MAX_WIDTH);
            if (navLeft) left += navigationWidth - NAV_MIN_WIDTH;
            else right += navigationWidth - NAV_MIN_WIDTH;
        }
        if (whole) this.mainBackgroundX = (width - left - mainBackgroundWidth - right) / 2 + left;
        return true;
    }

    private boolean centersWholeOverlay() {
        return switch (EnhancedStorageConfig.overlayCentering) {
            case WHOLE_OVERLAY -> true;
            case STORAGE_ONLY -> false;
            case WHOLE_WHEN_SIDE -> inventoryPosition != EnhancedStorageConfig.InventoryPosition.BOTTOM;
        };
    }

    private static boolean navigationSharesSide(EnhancedStorageConfig.InventoryPosition position) {
        return switch (EnhancedStorageConfig.navigationPanel) {
            case LEFT -> position == EnhancedStorageConfig.InventoryPosition.LEFT;
            case RIGHT -> position == EnhancedStorageConfig.InventoryPosition.RIGHT;
            case DISABLED -> false;
        };
    }

    private boolean showOverview() {
        return EnhancedStorageConfig.showStorageOverviewCard && !riftContext;
    }

    private int sideStackHeight() {
        int panels = INVENTORY_HEIGHT + PANEL_TOP;
        if (showOverview()) panels += LayoutSpacing.columnGap() + OVERVIEW_HEIGHT + PANEL_TOP;
        return panels + LayoutSpacing.columnGap() + BUTTON_SIZE;
    }

    private SpriteComponent buildMainPanel(IScreen screen) {
        SpriteComponent mainBackground = new SpriteComponent(mainBackgroundX, mainBackgroundY,
                mainBackgroundWidth, mainBackgroundHeight,
                OverlayTheme.sprite(OverlayTheme.MAIN_PANEL));
        screen.addComponent(mainBackground);
        return mainBackground;
    }

    private void buildNavigationPanel(IScreen screen, SpriteComponent mainBackground, List<LayoutEntry> entries) {
        if (navigationWidth == 0) return;
        boolean left = EnhancedStorageConfig.navigationPanel == EnhancedStorageConfig.NavigationPanelSide.LEFT;
        int x = left ? mainBackgroundX - LayoutSpacing.panelGap() - navigationWidth : mainBackgroundX + mainBackgroundWidth + LayoutSpacing.panelGap();
        int height = mainBackground.getHeight();
        if (navigationStacked) {
            x = columnX;
            height -= sideStackHeight() + LayoutSpacing.columnGap();
        }

        SpriteComponent panel = new SpriteComponent(x, mainBackgroundY, navigationWidth, height,
                OverlayTheme.sprite(OverlayTheme.PAGE_CARD_IDLE));
        screen.addComponent(panel);
        this.navigationBounds = new Rect(x, mainBackgroundY, navigationWidth, height);

        boolean hasSections = entries.stream().anyMatch(LayoutEntry.Section.class::isInstance);
        String toggle = !hasSections ? null : anySectionOpen(entries) ? "Collapse all" : "Expand all";
        NavigationTitleComponent title = new NavigationTitleComponent(NAV_INSET, NAV_INSET,
                navigationWidth - NAV_INSET * 2, toggle);
        title.updateParentPosition(panel.getTotalX(), panel.getTotalY(), panel.getWidth(), panel.getHeight());
        panel.addComponent(title);
        this.navigationTitle = title;

        int listTop = NAV_INSET * 2 + NavigationTitleComponent.HEIGHT;
        PageViewport list = new PageViewport(navigationWidth - NAV_INSET * 2, height - listTop - NAV_INSET, 0,
                state::setNavigationScroll);
        this.navigationParentX = panel.getTotalX() + NAV_INSET;
        this.navigationParentY = panel.getTotalY() + listTop;
        panel.addWidget(list);
        list.setPageLayer(this::pageLayerEnabled, () -> null);
        this.navigationList = list;
    }

    private List<LayoutEntry> collectEntries(@Nullable StorageKey liveKey) {
        List<StorageKey> keys = Stream.concat(
                        Stream.ofNullable(liveKey),
                        Stream.concat(
                                PageCache.getInstance().all().keySet().stream(),
                                PageCache.getInstance().allKnown().stream())).filter(Objects::nonNull)
                .filter(this::inContext)
                .distinct()
                .sorted(StorageKey.DISPLAY_ORDER)
                .toList();

        // Legacy per-page positions only apply until the player saves an order for this context.
        List<LayoutEntry> entries = PageLayout.getInstance().resolve(PageLayout.Context.of(riftContext), keys)
                .orElseGet(() -> applyCustomOrder(keys).stream().<LayoutEntry>map(LayoutEntry.Page::new).toList());
        return closeSections(entries);
    }

    public void saveOrder(List<LayoutEntry> order) {
        PageLayout.getInstance().setOrder(PageLayout.Context.of(riftContext), closeSections(LayoutOps.normalize(order)));
        for (StorageKey key : pageKeys(order)) PageOrder.getInstance().clear(key);
        ProfileSession.getInstance().saveAll();
    }

    List<GridCellComponent> getCells() {
        return cells;
    }

    List<GridCellComponent> getVisibleCells() {
        return visibleCells;
    }

    List<LayoutEntry> currentEntries() {
        return entriesOf(cells);
    }

    /**
     * The index in {@link #currentEntries()} that matches a boundary between cells, never inside a stack.
     */
    int entryIndexOfCell(int cellIndex) {
        return entriesBeforeCell(cellIndex).size();
    }

    List<LayoutEntry> entriesBeforeCell(int cellIndex) {
        return entriesOf(cells.subList(0, cellIndex));
    }

    /**
     * Shows another layout without saving it, reusing the existing cells. Stacks are re-tagged from the entries.
     */
    void previewEntries(List<LayoutEntry> entries) {
        Map<StorageKey, GridCellComponent> pages = new HashMap<>();
        Map<LayoutEntry, GridCellComponent> singles = new HashMap<>();
        Deque<GridCellComponent> ends = new ArrayDeque<>();
        for (GridCellComponent cell : cells) {
            if (cell instanceof PageCardComponent card) pages.put(card.getKey(), cell);
            else if (cell instanceof SectionEndComponent) ends.add(cell);
            else singles.put(cell.entry(), cell);
        }
        List<GridCellComponent> next = new ArrayList<>(cells.size());
        for (LayoutEntry entry : entries) {
            switch (entry) {
                case LayoutEntry.Page(StorageKey key) -> addCard(next, pages, key, GridCellComponent.NO_STACK);
                case LayoutEntry.Stack(List<StorageKey> keys) -> {
                    int tag = nextStackTag++;
                    for (StorageKey key : keys) addCard(next, pages, key, tag);
                }
                case LayoutEntry.SectionEnd _ -> {
                    GridCellComponent end = ends.poll();
                    if (end != null) next.add(end);
                }
                default -> {
                    GridCellComponent cell = singles.get(entry);
                    if (cell != null) next.add(cell);
                }
            }
        }
        previewOrder(next);
    }

    private static void addCard(List<GridCellComponent> into, Map<StorageKey, GridCellComponent> pages,
                                StorageKey key, int tag) {
        GridCellComponent card = pages.get(key);
        if (card == null) return;
        card.setStackTag(tag);
        into.add(card);
    }

    /**
     * The page and every page stacked with it, top to bottom.
     */
    List<StorageKey> stackMates(StorageKey key) {
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i) instanceof PageCardComponent card && card.getKey().equals(key)) {
                List<StorageKey> keys = new ArrayList<>();
                for (int j = stackStart(cells, i); j < stackEnd(cells, i); j++) {
                    if (cells.get(j) instanceof PageCardComponent mate) keys.add(mate.getKey());
                }
                return keys;
            }
        }
        return List.of(key);
    }

    int nextSpacerId() {
        int max = 0;
        for (GridCellComponent cell : cells) {
            if (cell.entry() instanceof LayoutEntry.Spacer(int id, int slots)) max = Math.max(max, id);
        }
        return max + 1;
    }

    int nextSectionId() {
        int max = 0;
        for (GridCellComponent cell : cells) {
            if (cell.entry() instanceof LayoutEntry.Section section) max = Math.max(max, section.id());
        }
        return max + 1;
    }

    boolean isPastLastCell(double x, double y) {
        if (rows.isEmpty() || !Rect.contains(getPageOverviewBounds(), x, y)) return false;
        Rect last = rows.getLast().last().bounds();
        return y >= last.bottom() || (y >= last.y() && x >= last.right());
    }

    @Nullable SectionSlot sectionSlotAt(double x, double y) {
        return editMode ? boundaryAt(x, y, false) : null;
    }

    /**
     * The row boundary under the cursor that isn't inside a section, or null; its index is an insert
     * position in {@link #getCells()}. With {@code nextToSectionsOnly}, boundaries between two ungrouped
     * rows are skipped.
     */
    @Nullable SectionSlot boundaryAt(double x, double y, boolean nextToSectionsOnly) {
        Rect viewport = getPageOverviewBounds();
        if (viewport == null || rows.isEmpty()) return null;
        Rect area = boundaryArea();
        if (!area.contains(x, y) || x >= area.x() + frameWidth) return null;

        int spacing = LayoutSpacing.cardSpacing();
        for (int i = 0; i < rows.size(); i++) {
            PlacedRow row = rows.get(i);
            PlacedRow above = i == 0 ? null : rows.get(i - 1);
            if (!canSplitBefore(row, above, nextToSectionsOnly)) continue;
            if (above != null && !viewport.contains(x, y)) continue;
            int top = row.component().getTotalY();
            int zoneTop = above == null ? area.y() : top - spacing - SECTION_LINE_REACH;
            if (y >= zoneTop && y < top + SECTION_LINE_REACH) {
                return new SectionSlot(cells.indexOf(row.first()), area.x(), top - (spacing + 1) / 2, frameWidth);
            }
        }

        PlacedRow last = rows.getLast();
        int bottom = last.component().getTotalY() + last.component().getHeight();
        if (y >= bottom - SECTION_LINE_REACH) {
            return new SectionSlot(cells.size(), area.x(), bottom + (spacing + 1) / 2 - 1, frameWidth);
        }
        return null;
    }

    Rect boundaryArea() {
        Rect viewport = Objects.requireNonNull(getPageOverviewBounds());
        int padding = LayoutSpacing.innerPadding();
        return new Rect(viewport.x(), viewport.y() - padding, viewport.width(), viewport.height() + padding * 2);
    }

    @Nullable PlacedRow ungroupedRowAt(double x, double y) {
        if (!Rect.contains(getPageOverviewBounds(), x, y)) return null;
        for (PlacedRow row : rows) {
            if (row.section() == null && row.bounds().contains(x, y)) return row;
        }
        return null;
    }

    int spacerSlotAt(double x, double y) {
        if (!Rect.contains(getPageOverviewBounds(), x, y)) return -1;
        for (PlacedRow row : rows) {
            if (row.first() instanceof SectionHeaderComponent || !row.bounds().contains(x, y)) continue;
            GridCellComponent last = null;
            for (GridCellComponent cell : row.cells()) {
                Rect b = cell.bounds();
                if (y >= b.y() && y < b.bottom() && (last == null || b.right() > last.bounds().right())) last = cell;
            }
            if (last == null) return -1;
            boolean besideLast = x >= last.bounds().right();
            int rowRight = row.component().getTotalX() + contentInset + rowContentWidth;
            boolean hasRoom = last.bounds().right() + LayoutSpacing.cardSpacing() + cardWidth <= rowRight;
            return hasRoom && besideLast ? stackEnd(cells, cells.indexOf(last)) : -1;
        }
        return -1;
    }

    void refresh() {
        if (pageOverview != null) applySearch(activeQuery());
    }

    private boolean inContext(StorageKey key) {
        return key.type() != StorageKey.Type.STORAGE_INDEX && riftContext == (key.type() == StorageKey.Type.RIFT);
    }

    public boolean isRiftContext() {
        return riftContext;
    }

    public int customCount(ResetPart part) {
        List<LayoutEntry> entries = currentEntries();
        return switch (part) {
            case ORDER -> {
                List<StorageKey> pages = pageKeys(entries);
                List<StorageKey> sorted = pages.stream().sorted(StorageKey.DISPLAY_ORDER).toList();
                int moved = 0;
                for (int i = 0; i < pages.size(); i++) {
                    if (!pages.get(i).equals(sorted.get(i))) moved++;
                }
                yield moved;
            }
            case NAMES -> (int) PageNames.getInstance().all().keySet().stream().filter(this::inContext).count();
            case WIDTHS -> (int) PageWidths.getInstance().all().keySet().stream().filter(this::inContext).count();
            case SECTIONS -> (int) entries.stream().filter(LayoutEntry.Section.class::isInstance).count();
            case SPACERS -> (int) entries.stream().filter(LayoutEntry.Spacer.class::isInstance).count();
            case STACKS -> (int) entries.stream().filter(LayoutEntry.Stack.class::isInstance).count();
        };
    }

    public void resetLayout(Set<ResetPart> parts) {
        List<LayoutEntry> entries = currentEntries();
        if (parts.contains(ResetPart.ORDER) || parts.contains(ResetPart.STACKS)) entries = LayoutOps.unstackAll(entries);
        if (parts.contains(ResetPart.ORDER)) {
            Iterator<StorageKey> sorted = pageKeys(entries).stream().sorted(StorageKey.DISPLAY_ORDER).iterator();
            entries.replaceAll(entry -> entry instanceof LayoutEntry.Page ? new LayoutEntry.Page(sorted.next()) : entry);
        }
        entries.removeIf(entry -> switch (entry) {
            case LayoutEntry.Section _, LayoutEntry.SectionEnd _ -> parts.contains(ResetPart.SECTIONS);
            case LayoutEntry.Spacer _ -> parts.contains(ResetPart.SPACERS);
            case LayoutEntry.Page _, LayoutEntry.Stack _ -> false;
        });

        if (parts.stream().anyMatch(p -> p != ResetPart.NAMES && p != ResetPart.WIDTHS)) {
            List<StorageKey> pages = pageKeys(entries);
            boolean onlyDefaultPages = entries.size() == pages.size()
                    && pages.equals(pages.stream().sorted(StorageKey.DISPLAY_ORDER).toList());
            if (onlyDefaultPages) {
                PageLayout.getInstance().clear(PageLayout.Context.of(riftContext));
                for (StorageKey key : List.copyOf(PageOrder.getInstance().all().keySet())) {
                    if (inContext(key)) PageOrder.getInstance().clear(key);
                }
            } else {
                saveOrder(entries);
            }
        }
        if (parts.contains(ResetPart.NAMES)) {
            for (StorageKey key : List.copyOf(PageNames.getInstance().all().keySet())) {
                if (inContext(key)) PageNames.getInstance().clear(key);
            }
        }
        if (parts.contains(ResetPart.WIDTHS)) {
            for (StorageKey key : List.copyOf(PageWidths.getInstance().all().keySet())) {
                if (inContext(key)) PageWidths.getInstance().clear(key);
            }
        }
        ProfileSession.getInstance().saveAll();
    }

    /**
     * Builds every cell once for the unfiltered list; search later picks which instances go into rows.
     */
    private void buildCells(List<LayoutEntry> entries, @Nullable StorageKey liveKey, int liveRows) {
        for (int i = 0; i < entries.size(); i++) {
            switch (entries.get(i)) {
                case LayoutEntry.Spacer spacer ->
                        cells.add(new SpacerCardComponent(spacerWidth(spacer), UNCACHED_CARD_HEIGHT, spacer));
                case LayoutEntry.Page(StorageKey key) -> cells.add(buildPageCard(key, liveKey, liveRows));
                case LayoutEntry.Stack(List<StorageKey> keys) -> {
                    int tag = nextStackTag++;
                    for (StorageKey key : keys) {
                        PageCardComponent card = buildPageCard(key, liveKey, liveRows);
                        card.setStackTag(tag);
                        cells.add(card);
                    }
                }
                case LayoutEntry.Section section -> cells.add(buildSectionHeader(entries, i, section, liveKey));
                case LayoutEntry.SectionEnd _ -> cells.add(new SectionEndComponent(frameWidth, 0));
            }
        }
    }

    private SectionHeaderComponent buildSectionHeader(List<LayoutEntry> entries, int index,
                                                      LayoutEntry.Section section, @Nullable StorageKey liveKey) {
        int pages = 0;
        int free = 0;
        boolean containsLive = false;
        for (int i = index + 1; i < entries.size()
                && !(entries.get(i) instanceof LayoutEntry.Section || entries.get(i) instanceof LayoutEntry.SectionEnd); i++) {
            for (StorageKey key : LayoutOps.keysOf(entries.get(i))) {
                pages++;
                free += freeSlots(key);
                containsLive |= key.equals(liveKey);
            }
        }
        // The open page's slots are placed on its card, so its section stays open until another page is opened.
        boolean collapsed = section.collapsed() && !containsLive;
        return new SectionHeaderComponent(frameWidth, section, collapsed, editMode, pages, free);
    }

    private PageCardComponent buildPageCard(StorageKey key, @Nullable StorageKey liveKey, int liveRows) {
        boolean isLive = key.equals(liveKey);

        boolean cached = PageCache.getInstance().get(key).isPresent();
        List<ItemStack> items = isLive
                ? List.of()
                : PageCache.getInstance().get(key)
                .map(PageCache.CachedPage::items).orElse(List.of());

        int slotCount = slotCount(key);
        int slotsAcross = slotsAcross(key);
        int cardHeight = cardHeightFor(key);

        PageCardComponent pageCard = new PageCardComponent(
                0, 0,
                cardWidthFor(key), cardHeight,
                key, state,
                items, slotCount,
                isLive || cached,
                isLive,
                !editMode,
                CARD_BORDER, titleStyle(), slotsAcross, LayoutSpacing.slotSize(),
                actions.onCardClick()
        );
        if (isLive) this.openCard = pageCard;
        if (editMode && EnhancedStorageConfig.hideItemTooltipsInEditMode) pageCard.disableItemTooltips();
        return pageCard;
    }

    private void buildPageViewport(SpriteComponent mainBackground) {
        int padding = LayoutSpacing.innerPadding();
        PageViewport viewport = new PageViewport(
                mainBackgroundWidth - padding * 2,
                mainBackground.getHeight() - padding * 2,
                LayoutSpacing.cardSpacing(), state::setScrollAmount);

        this.pageOverviewParentX = mainBackground.getTotalX() + padding;
        this.pageOverviewParentY = mainBackground.getTotalY() + padding;
        mainBackground.addWidget(viewport);
        viewport.setBackgroundPainter(this::paintSectionFrames);
        viewport.setPageLayer(this::pageLayerEnabled,
                () -> new PageLayerInputs(state.getOpenKey(), CopyHighlight.target()));
        this.pageOverview = viewport;

        reflowRows(activeQuery());
        viewport.setScrollAmount(state.getScrollAmount());
    }

    private boolean pageLayerEnabled() {
        return EnhancedStorageConfig.cachePageRendering;
    }

    // A remembered search could hide or dim pages the player is trying to arrange. So we set the search box to empty when entering edit mode and restore it when leaving.
    private String activeQuery() {
        return editMode ? "" : state.getSearchQuery();
    }

    private void buildBottomPanels(IScreen screen, int width, SpriteComponent mainBackground) {
        boolean showOverview = showOverview();

        int inventoryX;
        int inventoryY;
        int overviewX;
        int overviewY;
        if (inventoryPosition == EnhancedStorageConfig.InventoryPosition.BOTTOM) {
            inventoryX = (width / 2) - (INVENTORY_WIDTH / 2);
            overviewX = inventoryX - OVERVIEW_WIDTH;

            // When the overview would stick out past the main panel's left edge, shift the pair right just far enough
            // to line it up, so the inventory stays as close to the centre as it can. On a panel too narrow for that,
            // the pair is centred under it instead.
            if (showOverview && overviewX < mainBackgroundX) {
                int mainCenterX = mainBackgroundX + mainBackgroundWidth / 2;
                inventoryX = Math.min(mainBackgroundX + OVERVIEW_WIDTH, mainCenterX - (INVENTORY_WIDTH + OVERVIEW_WIDTH) / 2 + OVERVIEW_WIDTH);
                overviewX = inventoryX - OVERVIEW_WIDTH;
            }
            inventoryY = mainBackgroundY + mainBackground.getHeight();
            overviewY = inventoryY;
        } else {
            inventoryX = columnX;
            inventoryY = mainBackgroundY + mainBackground.getHeight() - sideStackHeight();
            overviewX = inventoryX;
            overviewY = inventoryY + INVENTORY_HEIGHT + PANEL_TOP + LayoutSpacing.columnGap();
        }

        boolean withTop = panelTop > 0;
        String inventorySprite = editMode
                ? (withTop ? OverlayTheme.STORAGE_INVENTORY_FILLED_TOP : OverlayTheme.STORAGE_INVENTORY_FILLED)
                : (withTop ? OverlayTheme.STORAGE_INVENTORY_TOP : OverlayTheme.STORAGE_INVENTORY);
        SpriteComponent inventory = new SpriteComponent(inventoryX, inventoryY, INVENTORY_WIDTH, INVENTORY_HEIGHT + panelTop,
                OverlayTheme.sprite(inventorySprite));
        screen.addComponent(inventory);
        this.inventoryPanel = inventory;
        this.inventoryBounds = new Rect(inventory.getTotalX(), inventory.getTotalY(),
                inventory.getWidth(), inventory.getHeight());

        SpriteComponent overview = null;
        if (showOverview) {
            overview = new CachedPanelComponent(overviewX, overviewY, OVERVIEW_WIDTH, OVERVIEW_HEIGHT + panelTop,
                    OverlayTheme.sprite(withTop ? OverlayTheme.STORAGE_OVERVIEW_TOP : OverlayTheme.STORAGE_OVERVIEW),
                    this::pageLayerEnabled);
            screen.addComponent(overview);
        }
        this.overviewPanel = overview;
        this.overviewBounds = overview == null ? null
                : new Rect(overview.getTotalX(), overview.getTotalY(), overview.getWidth(), overview.getHeight());
    }

    private void addOverviewIndexItems(@Nullable StorageKey liveKey) {
        SpriteComponent overview = overviewPanel;
        // On the live index page the real menu slots fill this grid instead.
        if (overview == null || (liveKey != null && liveKey.type() == StorageKey.Type.STORAGE_INDEX)) return;

        List<ItemStack> items = PageCache.getInstance().get(new StorageKey(StorageKey.Type.STORAGE_INDEX, 0))
                .map(PageCache.CachedPage::items)
                .orElse(List.of());

        for (int idx = 0; idx < items.size(); idx++) {
            ItemStack stack = items.get(idx);
            if (stack == null || stack.isEmpty()) continue;

            int x = IndexGrid.cellX(idx % IndexGrid.COLUMNS) + StorageSlotComponent.ITEM_INSET;
            int y = IndexGrid.rowY(idx / IndexGrid.COLUMNS) + StorageSlotComponent.ITEM_INSET + panelTop;
            TooltipItemComponent item = new TooltipItemComponent(x, y, ItemModels.withResolvableModel(stack), true);
            item.setTooltipEnabled(EnhancedStorageConfig.showItemTooltipsOnCachedItems
                    && (!editMode || !EnhancedStorageConfig.hideItemTooltipsInEditMode));
            item.updateParentPosition(overview.getTotalX(), overview.getTotalY(),
                    overview.getWidth(), overview.getHeight());
            overview.addComponent(item);
        }
    }

    // The inventory panel is empty in edit mode, so we reuse it to show hints about how to use edit mode.
    private void addEditModeHints(SpriteComponent panel) {
        int actionWidth = EDIT_MODE_HINTS.stream().mapToInt(h -> font.width(h.action())).max().orElse(0);
        int resultX = 7 + actionWidth + 8;
        int y = 17;
        for (Hint hint : EDIT_MODE_HINTS) {
            addPanelText(panel, 7, y, hint.action(), OverlayTheme.titleColor());
            addPanelText(panel, resultX, y, hint.result(), OverlayTheme.secondaryTextColor());
            y += font.lineHeight + 2;
        }
    }

    private void ensureWarmupQueue() {
        if (warmupQueue != null) return;
        warmupQueue = new ArrayDeque<>();
        PageCache.getInstance().all().values().forEach(page -> warmupQueue.addAll(page.items()));
    }

    /**
     * The search box instance survives rebuilds so typed text and focus are kept.
     */
    private void buildSearchBox(IScreen screen) {
        if (this.searchBox == null) {
            EditBoxWidget box = new EditBoxWidget(font, 70, 1, 100, 12, Component.literal("Search"));
            box.setValue(state.getSearchQuery());
            box.setResponder(text -> {
                if (text.equals(this.state.getSearchQuery())) return;
                this.state.setSearchQuery(text);
                searchDirty = true;
            });
            this.searchBox = box;
        }
        this.searchBox.uilib$updateParentPosition(inventoryPanel.getTotalX(), inventoryPanel.getTotalY() + panelTop);
        screen.addWidget(this.searchBox);
    }

    /**
     * Buttons sit in a row to the right of the player inventory panel, or at the bottom of the panel column when it is beside the grid.
     */
    private void buildButtons(IScreen screen, int width, int height) {
        final int btnSize = BUTTON_SIZE;
        final int btnGap = BUTTON_GAP;
        final int iconSize = 16;
        boolean beside = inventoryPosition != EnhancedStorageConfig.InventoryPosition.BOTTOM;
        final int btnY = beside
                ? mainBackgroundY + mainBackgroundHeight - btnSize
                : inventoryPanel.getTotalY() + 2;
        final int rowStartX = beside
                ? columnX
                : inventoryPanel.getTotalX() + INVENTORY_WIDTH + 2;
        int btnX = rowStartX;

        if (EnhancedStorageConfig.showToolkitButton && !riftContext && !editMode) {
            ItemButtonComponent toolkit = new ItemButtonComponent(btnX, btnY, btnSize, btnSize, buildToolkitIcon());
            this.toolkitButtonBounds = placeButton(screen, toolkit, btnX, btnY, btnSize, width, height);
            btnX += btnSize + btnGap;
        }

        if (EnhancedStorageConfig.showSettingsButton && !riftContext) {
            IconButtonComponent settings = new IconButtonComponent(
                    btnX, btnY, btnSize, btnSize,
                    getSettingsIcon(), iconSize,
                    List.of(Component.literal("Settings").withStyle(s -> s.withColor(0x55FF55).withItalic(false))));
            this.settingsButtonBounds = placeButton(screen, settings, btnX, btnY, btnSize, width, height);
            btnX += btnSize + btnGap;
        }

        if (EnhancedStorageConfig.showThemeButton && !riftContext) {
            IconButtonComponent theme = new IconButtonComponent(
                    btnX, btnY, btnSize, btnSize,
                    OverlayTheme.icon(), iconSize,
                    List.of(
                            Component.literal("Theme: " + OverlayTheme.displayName())
                                    .withStyle(s -> s.withColor(0x55FF55).withItalic(false)),
                            Component.literal("Click to cycle")
                                    .withStyle(s -> s.withColor(0xFFAA00).withItalic(false))));
            this.themeButtonBounds = placeButton(screen, theme, btnX, btnY, btnSize, width, height);
            btnX += btnSize + btnGap;
        }

        // We always show the edit button in edit mode, so the player can exit it. Otherwise, only show it if the config allows.
        if (EnhancedStorageConfig.showEditButton || editMode) {
            IconButtonComponent edit = new IconButtonComponent(
                    btnX, btnY, btnSize, btnSize,
                    getEditIcon(), iconSize,
                    List.of(Component.literal(editMode ? "Exit Edit Mode" : "Edit Layout")
                            .withStyle(s -> s.withColor(0x55FF55).withItalic(false))));
            this.editButtonBounds = placeButton(screen, edit, btnX, btnY, btnSize, width, height);
            btnX += btnSize + btnGap;
        }

        if (editMode) {
            // The column has one button row, so Reset joins it instead of going below.
            int resetX = beside ? btnX : rowStartX;
            int resetY = beside ? btnY : btnY + btnSize + btnGap;
            IconButtonComponent reset = new IconButtonComponent(
                    resetX, resetY, btnSize, btnSize,
                    getResetIcon(), iconSize,
                    List.of(Component.literal("Reset Layout").withStyle(s -> s.withColor(0x55FF55).withItalic(false)),
                            Component.literal("Choose what to restore to default")
                                    .withStyle(s -> s.withColor(0xFFAA00).withItalic(false))));
            this.resetButtonBounds = placeButton(screen, reset, resetX, resetY, btnSize, width, height);
        }
    }

    public boolean isOverResetButton(double x, double y) {
        return Rect.contains(resetButtonBounds, x, y);
    }

    private void applySearch(String query) {
        if (pageOverview == null) return;

        reflowRows(query);
        // Hiding cards shrinks the content, so an old scroll offset can now point past the end.
        pageOverview.setScrollAmount(pageOverview.scrollAmount());
    }

    /**
     * Re-packs the rows in a new card order without rebuilding the cards.
     */
    void previewOrder(List<GridCellComponent> order) {
        cells.clear();
        cells.addAll(order);
        applySearch(activeQuery());
    }

    public void tick() {
        if (warmupQueue != null && !warmupQueue.isEmpty()) {
            ItemSearch.warmUp(warmupQueue, WARMUP_BUDGET_MS);
        }
        if (searchDirty) {
            searchDirty = false;
            applySearch(state.getSearchQuery());
        }
    }

    public boolean handleButtonClick(MouseButtonEvent event) {
        double x = event.x();
        double y = event.y();
        int button = event.button();

        if ((button == 0 || button == 1) && Rect.contains(toolkitButtonBounds, x, y)) {
            actions.sendCommand().accept(button == 0 ? "farmingtoolkit" : "huntingtoolkit");
            return true;
        }
        if (button != 0) return false;

        if (handleSectionClick(x, y)) return true;
        if (handleNavigationClick(x, y)) return true;
        if (Rect.contains(settingsButtonBounds, x, y)) {
            actions.openSettings().run();
            return true;
        }
        if (Rect.contains(themeButtonBounds, x, y)) {
            OverlayTheme.cycle();
            actions.rebuild().run();
            return true;
        }
        if (Rect.contains(editButtonBounds, x, y)) {
            actions.toggleEditMode().run();
            return true;
        }
        return false;
    }

    /**
     * Packs the visible cells into rows no wider than {@code rowContentWidth}, replacing whatever the scroll container held
     * before. Cell instances are reused; only the lightweight row wrappers are allocated.
     */
    private void reflowRows(String query) {
        boolean searching = !query.isBlank();
        boolean dim = searching && EnhancedStorageConfig.searchMode == EnhancedStorageConfig.SearchMode.DIM;
        boolean copyFilter = !copyFilterTarget.isEmpty();
        searchMisses.clear();
        List<GridCellComponent> shown = new ArrayList<>();
        boolean sectionNameMatches = false;
        for (GridCellComponent cell : cells) {
            if (cell instanceof SectionHeaderComponent header) {
                sectionNameMatches = searching && nameMatches(header.entry().name(), query);
            } else if (cell instanceof SectionEndComponent) {
                sectionNameMatches = false;
            }

            if (cell instanceof PageCardComponent card) {
                // Filtering by a held item hides the page whatever the search says.
                if (copyFilter && !containsCopy(card)) continue;
                boolean itemMatch = searching && pageMatches(card.getKey(), query);
                boolean nameMatch = sectionNameMatches || (searching && customNameMatches(card.getKey(), query));
                // A page found by its name is the result, so the items aren't dimmed.
                card.applySearch(itemMatch || !nameMatch ? query : "");
                if (!searching || card.isLive() || itemMatch || nameMatch) {
                    shown.add(card);
                } else if (dim) {
                    shown.add(card);
                    searchMisses.add(card);
                }
            } else if ((!copyFilter && (!searching || dim)) || isBoundary(cell)) {
                shown.add(cell);
            }
        }
        if (copyFilter || (searching && !dim)) hideEmptySections(shown);
        if (dim) dimEmptySections(shown);
        if (shown.stream().noneMatch(PageCardComponent.class::isInstance)) shown.clear();

        boolean collapseForDrag = draggedSection != null && EnhancedStorageConfig.collapseSectionsWhileDragging;
        visibleCells.clear();
        visibleCards.clear();
        boolean hidden = false;
        for (GridCellComponent cell : shown) {
            if (cell instanceof SectionHeaderComponent header) {
                hidden = (header.isCollapsed() && copyFilterTarget.isEmpty()) || collapseForDrag;
            } else if (cell instanceof SectionEndComponent) {
                hidden = false;
            } else if (hidden) {
                continue;
            }
            visibleCells.add(cell);
            if (cell instanceof PageCardComponent card) visibleCards.add(card);
        }

        pageOverview.clearComponents();
        sectionFrames.clear();
        rows.clear();
        liveRowTop = -1;
        liveRowBottom = -1;

        if (visibleCells.isEmpty()) {
            pageOverview.addComponent(buildEmptyRow(query));
        }

        int spacing = LayoutSpacing.cardSpacing();
        List<List<GridCellComponent>> groups = groupStacks(visibleCells);
        List<RowFlow.Box> boxes = new ArrayList<>(groups.size());
        for (List<GridCellComponent> group : groups) boxes.add(boxOf(group, spacing));

        int contentY = 0;
        int start = 0;
        SectionHeaderComponent section = null;
        while (start < groups.size()) {
            GridCellComponent first = groups.get(start).getFirst();
            if (first instanceof SectionEndComponent) {
                section = null;
                start++;
                continue;
            }

            if (isFillSpacer(first)) {
                ((SpacerCardComponent) first).setWidth(0);
                start++;
                continue;
            }

            RowFlow.Row flow = first instanceof SectionHeaderComponent ? null
                    : RowFlow.pack(boxes, start, nextBoundary(groups, start), rowContentWidth, spacing, UNCACHED_CARD_HEIGHT);
            int end = flow == null ? start + 1 : flow.end();
            boolean endsSection = endsSectionAt(groups, end);

            int rowHeight;
            if (first instanceof SectionHeaderComponent header) {
                section = header;
                header.setOpen(!endsSection);
                rowHeight = endsSection ? SectionHeaderComponent.CLOSED_HEIGHT : SectionHeaderComponent.OPEN_HEIGHT;
            } else {
                // A section's last row also holds the frame's bottom padding.
                rowHeight = section != null && endsSection ? flow.height() + SECTION_INSET : flow.height();
            }

            List<GridCellComponent> rowCells = new ArrayList<>();
            EmptyComponent row = new EmptyComponent(0, 0, frameWidth, rowHeight);
            for (int g = start; g < end; g++) {
                RowFlow.Spot spot = flow == null ? null : flow.spots().get(g - start);
                int cellY = spot == null ? 0 : spot.y();
                for (GridCellComponent cell : groups.get(g)) {
                    if (spot == null) {
                        cell.setX(0);
                    } else {
                        cell.setX(contentInset + spot.x());
                        if (cell instanceof SpacerCardComponent spacer) {
                            spacer.setSlotHeight(spot.lineHeight());
                            if (spacer.isFill()) spacer.setWidth(Math.max(0, rowContentWidth - spot.x()));
                        }
                    }
                    // Cells are reused between reflows and stacked ones sit lower, so y is reset every time.
                    // UI Lib propagates setY through the whole subtree, so it's skipped when unchanged.
                    if (cell.getY() != cellY) cell.setY(cellY);
                    cellY += cell.getHeight() + spacing;

                    // Remember the live page's row so it can be scrolled into view.
                    if (cell instanceof PageCardComponent card && card.isLive()) {
                        liveRowTop = contentY;
                        liveRowBottom = contentY + rowHeight;
                    }
                    row.addComponent(cell);
                    rowCells.add(cell);
                }
            }
            pageOverview.addComponent(row);
            rows.add(new PlacedRow(row, List.copyOf(rowCells), section));
            if (section != null && endsSection) sectionFrames.add(new SectionFrame(section, row));
            contentY += rowHeight + spacing;
            start = end;
        }

        // Rows were just re-parented, so push the container's position back down the tree.
        pageOverview.uilib$updateParentPosition(pageOverviewParentX, pageOverviewParentY);
        pageOverview.positionRows();
        refreshNavigation(shown);
    }

    /**
     * Lists what the grid shows (before collapsing), so search filters the panel too.
     */
    private void refreshNavigation(List<GridCellComponent> shown) {
        if (navigationList == null) return;
        navigationList.clearComponents();
        int width = navigationList.getWidth() - SCROLLBAR_WIDTH - SCROLLBAR_GAP;

        record Item(GridCellComponent cell, boolean grouped) {
        }
        List<Item> items = new ArrayList<>();
        boolean inSection = false;
        for (GridCellComponent cell : shown) {
            if (cell instanceof SectionHeaderComponent) inSection = true;
            else if (cell instanceof SectionEndComponent) inSection = false;
            if (cell instanceof SectionHeaderComponent || cell instanceof PageCardComponent) {
                items.add(new Item(cell, inSection));
            }
        }

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            Item previous = i > 0 ? items.get(i - 1) : null;
            Item next = i + 1 < items.size() ? items.get(i + 1) : null;

            if (previous != null && (item.cell() instanceof SectionHeaderComponent
                    || (!item.grouped() && previous.grouped()))) {
                navigationList.addComponent(new EmptyComponent(0, 0, width, NAV_GROUP_GAP));
            }

            boolean nextIsChild = next != null && next.grouped() && next.cell() instanceof PageCardComponent;
            boolean dimmed = searchMisses.contains(item.cell());
            if (item.cell() instanceof SectionHeaderComponent header) {
                navigationList.addComponent(NavigationRowComponent.section(width, header, nextIsChild, dimmed));
            } else if (item.cell() instanceof PageCardComponent card) {
                boolean open = card.isLive() || state.isOpen(card.getKey());
                navigationList.addComponent(
                        NavigationRowComponent.page(width, card, item.grouped(), nextIsChild, open, dimmed));
            }
        }

        navigationList.uilib$updateParentPosition(navigationParentX, navigationParentY);
        // The listener keeps the state equal to the current scroll, so this restores it after a rebuild and clamps it after a search.
        navigationList.setScrollAmount(state.getNavigationScroll());
    }

    private @Nullable NavigationRowComponent navigationRowAt(double x, double y) {
        if (navigationList == null) return null;
        Rect list = new Rect(navigationList.getX(), navigationList.getY(),
                navigationList.getWidth(), navigationList.getHeight());
        if (!list.contains(x, y)) return null;
        for (IComponent component : navigationList.getComponents()) {
            if (component instanceof NavigationRowComponent row && row.bounds().contains(x, y)) return row;
        }
        return null;
    }

    private boolean handleNavigationClick(double x, double y) {
        if (navigationTitle != null && navigationTitle.isOverToggle(x, y)) {
            List<LayoutEntry> entries = currentEntries();
            boolean collapse = anySectionOpen(entries);
            entries.replaceAll(entry -> entry instanceof LayoutEntry.Section s ? s.withCollapsed(collapse) : entry);
            saveOrder(entries);
            actions.rebuild().run();
            return true;
        }

        NavigationRowComponent row = navigationRowAt(x, y);
        if (row == null) return false;
        if (row.isOverTriangle(x, y) && row.target() instanceof SectionHeaderComponent header) {
            LayoutEntry.Section section = header.entry();
            replaceSection(section, section.withCollapsed(!section.collapsed()));
            return true;
        }
        scrollToCell(row.target());
        if (!editMode && row.target() instanceof PageCardComponent card && !state.isOpen(card.getKey())) {
            actions.onCardClick().accept(card.getKey());
        }
        return true;
    }

    private void scrollToCell(GridCellComponent target) {
        GridCellComponent cell = target;
        // A page in a collapsed section has no position, so its header stands in for it.
        if (!visibleCells.contains(cell)) {
            for (int i = cells.indexOf(target) - 1; i >= 0; i--) {
                if (cells.get(i) instanceof SectionHeaderComponent header) {
                    cell = header;
                    break;
                }
            }
        }
        if (!visibleCells.contains(cell)) return;
        pageOverview.setScrollAmount(pageOverview.scrollAmount() + cell.getTotalY() - pageOverview.getY());
    }

    // Dim mode keeps every header, so one with no matching page under it is dimmed instead of hidden.
    private void dimEmptySections(List<GridCellComponent> list) {
        boolean hitBelow = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            GridCellComponent cell = list.get(i);
            if (cell instanceof SectionHeaderComponent) {
                if (!hitBelow) searchMisses.add(cell);
                hitBelow = false;
            } else if (cell instanceof SectionEndComponent) {
                hitBelow = false;
            } else if (cell instanceof PageCardComponent && !searchMisses.contains(cell)) {
                hitBelow = true;
            }
        }
    }

    private void paintSectionFrames(GuiGraphicsExtractor graphics) {
        for (SectionFrame frame : sectionFrames) {
            Rect r = frame.bounds();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OverlayTheme.sprite(OverlayTheme.SECTION_FRAME),
                    r.x(), r.y(), r.width(), r.height());
        }
    }

    @Nullable SectionFrame sectionFrameAt(double x, double y) {
        if (!Rect.contains(getPageOverviewBounds(), x, y)) return null;
        for (SectionFrame frame : sectionFrames) {
            if (frame.bounds().contains(x, y)) return frame;
        }
        return null;
    }

    void setDraggedSection(@Nullable SectionHeaderComponent header) {
        SectionHeaderComponent anchor = header != null ? header : draggedSection;
        this.draggedSection = header;
        if (pageOverview == null) return;

        int before = anchor != null ? anchor.getTotalY() : 0;
        applySearch(activeQuery());
        // Sections above can collapse or expand, which would move the dragged bar away from the cursor.
        if (anchor != null) {
            pageOverview.setScrollAmount(pageOverview.scrollAmount() + anchor.getTotalY() - before);
        }
    }

    private boolean handleSectionClick(double x, double y) {
        if (!Rect.contains(getPageOverviewBounds(), x, y)) return false;
        for (GridCellComponent cell : visibleCells) {
            if (!(cell instanceof SectionHeaderComponent header)) continue;
            LayoutEntry.Section section = header.entry();
            if (header.isOverArrow(x, y)) {
                replaceSection(section, section.withCollapsed(!section.collapsed()));
                return true;
            }
            if (header.isOverTrash(x, y)) {
                replaceSection(section, null);
                return true;
            }
        }
        return false;
    }

    private void replaceSection(LayoutEntry.Section section, @Nullable LayoutEntry.Section replacement) {
        List<LayoutEntry> entries = currentEntries();
        int index = entries.indexOf(section);
        if (index < 0) return;
        if (replacement == null) entries.remove(index);
        else entries.set(index, replacement);
        saveOrder(entries);
        actions.rebuild().run();
    }

    public void extractOverlayTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (navigationTitle != null && navigationTitle.isOverToggle(mouseX, mouseY)) {
            boolean collapse = anySectionOpen(currentEntries());
            graphics.setComponentTooltipForNextFrame(font, List.of(
                            Component.literal(collapse ? "Collapse all sections" : "Expand all sections"),
                            Component.literal(collapse
                                            ? "Hides the pages inside every section in the grid."
                                            : "Shows the pages inside every section in the grid.")
                                    .withStyle(ChatFormatting.GRAY),
                            Component.literal("This list always shows every page.").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
            return;
        }
        NavigationRowComponent navigationRow = navigationRowAt(mouseX, mouseY);
        if (navigationRow != null) {
            if (navigationRow.target() instanceof PageCardComponent card) {
                graphics.setComponentTooltipForNextFrame(font, List.of(
                                Component.literal(navigationRow.name()),
                                Component.literal(emptySlotsDescription(card.getKey())).withStyle(ChatFormatting.GRAY)),
                        mouseX, mouseY);
            } else if (navigationRow.isTruncated()) {
                graphics.setTooltipForNextFrame(font, Component.literal(navigationRow.name()), mouseX, mouseY);
            }
            return;
        }

        if (!Rect.contains(getPageOverviewBounds(), mouseX, mouseY)) return;
        if (titleStyle() == EnhancedStorageConfig.PageTitleStyle.HIDDEN) {
            for (PageCardComponent card : visibleCards) {
                if (!card.bounds().contains(mouseX, mouseY)) continue;
                if (!pageItemAt(card, mouseX, mouseY).isEmpty()) return;
                String name = PageNames.getInstance().get(card.getKey()).orElse(card.getKey().displayName());
                graphics.setComponentTooltipForNextFrame(font, List.of(
                                Component.literal(TextUtils.colorCodes(name)),
                                Component.literal(emptySlotsDescription(card.getKey())).withStyle(ChatFormatting.GRAY)),
                        mouseX, mouseY);
                return;
            }
        }
        for (GridCellComponent cell : visibleCells) {
            if (!(cell instanceof SectionHeaderComponent header) || !header.bounds().contains(mouseX, mouseY)) continue;
            if (!header.isCollapsed() || !copyFilterTarget.isEmpty() || header.isOverArrow(mouseX, mouseY)
                    || header.isOverGrip(mouseX, mouseY) || header.isOverTrash(mouseX, mouseY)) return;

            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(TextUtils.colorCodes(header.entry().name())));
            for (int i = cells.indexOf(header) + 1; i < cells.size() && !isBoundary(cells.get(i)); i++) {
                if (cells.get(i) instanceof PageCardComponent card) {
                    String name = PageNames.getInstance().get(card.getKey()).orElse(card.getKey().displayName());
                    lines.add(Component.literal(TextUtils.colorCodes(name)).withStyle(ChatFormatting.GRAY));
                }
            }
            if (lines.size() > 1) graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
    }

    private static ItemStack pageItemAt(PageCardComponent card, double x, double y) {
        int index = card.slotIndexAt(x, y);
        if (index < 0) return ItemStack.EMPTY;
        return PageCache.getInstance().get(card.getKey())
                .map(PageCache.CachedPage::items)
                .filter(items -> index < items.size())
                .map(items -> items.get(index))
                .orElse(ItemStack.EMPTY);
    }

    public ItemStack hoveredCachedStack(double x, double y) {
        if (!Rect.contains(getPageOverviewBounds(), x, y)) return ItemStack.EMPTY;
        for (PageCardComponent card : visibleCards) {
            if (card.bounds().contains(x, y)) return card.stackAt(x, y);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Arrow keys open the neighbouring page as shown; Shift+1–9 opens that page in layout order.
     * Returns true when the key is a navigation key, even if there was nothing to open.
     */
    public boolean handleNavigationKey(KeyEvent event) {
        if (editMode) return false;
        int mods = event.modifiers()
                & (InputConstants.MOD_SHIFT | InputConstants.MOD_CONTROL | InputConstants.MOD_ALT | InputConstants.MOD_SUPER);
        int key = event.key();
        StorageKey target;
        if (mods == InputConstants.MOD_SHIFT && digitKey(key) > 0) {
            // Consumed even without a page, so vanilla doesn't also swap the hovered slot with the hotbar.
            target = pageByNumber(digitKey(key));
        } else if ((mods & ~InputConstants.MOD_ALT) == 0 && isArrow(key)) {
            // Alt is allowed, so arrows also step through pages filtered by a held item.
            target = pageInDirection(key);
        } else {
            return false;
        }
        // One page switch at a time; a held key would otherwise send a command per repeat.
        if (target != null && !state.isNavigating()) actions.onCardClick().accept(target);
        return true;
    }

    private @Nullable StorageKey pageByNumber(int number) {
        int count = 0;
        for (GridCellComponent cell : cells) {
            if (cell instanceof PageCardComponent card && ++count == number) return card.getKey();
        }
        return null;
    }

    private @Nullable StorageKey pageInDirection(int key) {
        if (visibleCards.isEmpty()) return null;
        PageCardComponent current = currentCard();
        if (current == null) return visibleCards.getFirst().getKey();
        int index = visibleCards.indexOf(current);
        return switch (key) {
            case InputConstants.KEY_LEFT -> index > 0 ? visibleCards.get(index - 1).getKey() : null;
            case InputConstants.KEY_RIGHT -> index < visibleCards.size() - 1 ? visibleCards.get(index + 1).getKey() : null;
            default -> verticalNeighbour(current, key == InputConstants.KEY_UP);
        };
    }

    private @Nullable PageCardComponent currentCard() {
        for (PageCardComponent card : visibleCards) {
            if (card.isLive() || state.isOpen(card.getKey())) return card;
        }
        return null;
    }

    // Rows can hold fewer cards or sit in other sections, so take the nearest row, then the closest centre in it.
    private @Nullable StorageKey verticalNeighbour(PageCardComponent current, boolean up) {
        int currentY = current.getTotalY();
        int centerX = current.getTotalX() + current.getWidth() / 2;
        PageCardComponent best = null;
        int bestRowDistance = Integer.MAX_VALUE;
        int bestXDistance = Integer.MAX_VALUE;
        for (PageCardComponent card : visibleCards) {
            int rowDistance = up ? currentY - card.getTotalY() : card.getTotalY() - currentY;
            if (rowDistance <= 0) continue;
            int xDistance = Math.abs(card.getTotalX() + card.getWidth() / 2 - centerX);
            if (rowDistance < bestRowDistance || (rowDistance == bestRowDistance && xDistance < bestXDistance)) {
                best = card;
                bestRowDistance = rowDistance;
                bestXDistance = xDistance;
            }
        }
        return best == null ? null : best.getKey();
    }

    public void updateCopyFilter() {
        ItemStack next = editMode ? ItemStack.EMPTY : CopyHighlight.filterTarget();
        if (next == copyFilterTarget || pageOverview == null) return;

        if (!next.isEmpty() && state.getScrollBeforeCopyFilter() == null) {
            state.setScrollBeforeCopyFilter(pageOverview.scrollAmount());
        }
        copyFilterTarget = next;
        applySearch(activeQuery());

        if (!next.isEmpty()) {
            pageOverview.setScrollAmount(0);
            return;
        }
        Double saved = state.getScrollBeforeCopyFilter();
        state.setScrollBeforeCopyFilter(null);
        if (saved != null) pageOverview.setScrollAmount(saved);
        scrollLiveCardIntoView();
    }

    private boolean containsCopy(PageCardComponent card) {
        return card.isLive() || PageCache.getInstance().get(card.getKey())
                .map(page -> page.items().stream().anyMatch(stack -> !stack.isEmpty()
                        && CopyHighlight.sameItem(copyFilterTarget, stack)))
                .orElse(false);
    }

    private EmptyComponent buildEmptyRow(String query) {
        Component message = query.isBlank()
                ? Component.literal(riftContext
                ? "No rift storage cached yet. Open a Rift Storage page first."
                : "No storage pages cached yet. Open an Ender Chest or Backpack first.")
                : Component.literal("No storage pages contain \"" + query + "\".");

        int rowHeight = titleAreaHeight + 8;
        int textWidth = Math.min(font.width(message), rowContentWidth);
        int textX = (rowContentWidth - textWidth) / 2;
        int textY = (rowHeight - font.lineHeight) / 2 + 20;

        TruncatedTextComponent emptyText = new TruncatedTextComponent(
                textX, textY, rowContentWidth - textX, message, OverlayTheme.titleColor());
        emptyText.setDrawShadow(OverlayTheme.titleShadow());

        EmptyComponent emptyRow = new EmptyComponent(0, 0, rowContentWidth, rowHeight);
        emptyRow.addComponent(emptyText);
        return emptyRow;
    }

    private boolean pageMatches(StorageKey key, String query) {
        if (!query.equals(pageMatchQuery)) {
            if (TextUtils.stripString(query).startsWith(TextUtils.stripString(pageMatchQuery))) {
                pageMatchCache.entrySet().removeIf(Map.Entry::getValue);
            } else {
                pageMatchCache.clear();
            }
            pageMatchQuery = query;
        }

        Boolean known = pageMatchCache.get(key);
        if (known != null) return known;

        boolean result = PageCache.getInstance().get(key)
                .map(page -> ItemSearch.anyMatch(page.items(), query))
                .orElse(false);
        pageMatchCache.put(key, result);
        return result;
    }

    // Scroll the live page into view if is not already in view
    public void scrollLiveCardIntoView() {
        if (pageOverview == null || liveRowTop < 0) return;
        if (EnhancedStorageConfig.autoScrollToOpenPage == EnhancedStorageConfig.AutoScrollMode.OFF) return;

        double scroll = pageOverview.scrollAmount();
        int viewHeight = pageOverview.getHeight();
        int rowHeight = liveRowBottom - liveRowTop;

        boolean fullyVisible = liveRowTop >= scroll && liveRowBottom <= scroll + viewHeight;
        boolean fullyHidden = liveRowBottom <= scroll || liveRowTop >= scroll + viewHeight;

        if (fullyVisible) return;
        if (EnhancedStorageConfig.autoScrollToOpenPage == EnhancedStorageConfig.AutoScrollMode.IF_FULLY_HIDDEN && !fullyHidden) {
            return;
        }

        if (liveRowTop < scroll || rowHeight >= viewHeight) {
            pageOverview.setScrollAmount(liveRowTop);
        } else if (liveRowBottom > scroll + viewHeight) {
            // Below the viewport — scroll the minimum amount so its bottom lands on the edge.
            pageOverview.setScrollAmount(liveRowBottom - viewHeight);
        }
        // else: already fully visible do nothing
    }

    public enum ResetPart {
        ORDER("Page order"),
        NAMES("Page names"),
        WIDTHS("Page widths"),
        SECTIONS("Sections"),
        SPACERS("Spacers"),
        STACKS("Stacked pages");

        private final String label;

        ResetPart(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private record Hint(String action, String result) {
    }

    private record PageLayerInputs(@Nullable StorageKey openKey, ItemStack copyTarget) {
    }

    record SectionSlot(int index, int x, int lineY, int width) {
    }

    record PlacedRow(EmptyComponent component, List<GridCellComponent> cells,
                     @Nullable SectionHeaderComponent section) {
        GridCellComponent first() {
            return cells.getFirst();
        }

        GridCellComponent last() {
            return cells.getLast();
        }

        Rect bounds() {
            return new Rect(component.getTotalX(), component.getTotalY(), component.getWidth(), component.getHeight());
        }
    }

    record SectionFrame(SectionHeaderComponent header, EmptyComponent lastRow) {
        Rect bounds() {
            int top = header.getTotalY();
            int bottom = lastRow.getTotalY() + lastRow.getHeight();
            return new Rect(header.getTotalX(), top, header.getWidth(), bottom - top);
        }
    }
}

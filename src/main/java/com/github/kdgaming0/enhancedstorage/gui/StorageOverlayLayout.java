package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.api.screen.IScreen;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.EmptyComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.component.text.TruncatedTextComponent;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.component.common.IconButtonComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.common.ItemButtonComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.navigation.NavigationRowComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.navigation.NavigationTitleComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.page.*;
import com.github.kdgaming0.enhancedstorage.gui.component.section.RowBreakComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.section.SectionEndComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.section.SectionHeaderComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.section.StackJoinComponent;
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
import net.fabricmc.loader.api.FabricLoader;
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
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Stream;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.MOD_ID;


public class StorageOverlayLayout {

    public static final boolean RRV_LOADED = FabricLoader.getInstance().isModLoaded("rrv");
    public static final boolean SKYBLOCKER_LOADED = FabricLoader.getInstance().isModLoaded("skyblocker");
    public static final int SLOTS_ACROSS = 9;
    public static final int SLOT_SIZE = 18;
    public static final int CARD_BORDER = 3;
    public static final int INNER_PADDING = 6;
    public static final int SCROLLBAR_WIDTH = 6;
    public static final int SCROLLBAR_GAP = 4;
    public static final int UNCACHED_CARD_HEIGHT = 50;
    private static final int INVENTORY_WIDTH = 176;
    private static final int INVENTORY_HEIGHT = 96;
    private static final int OVERVIEW_WIDTH = 176;
    private static final int OVERVIEW_HEIGHT = 85;
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
    private static final int NAV_GAP = 2;
    private static final int NAV_INSET = 2;
    private static final int NAV_GROUP_GAP = 4;
    private static final int NAV_MIN_WIDTH = 70;
    private static final int NAV_MAX_WIDTH = 120;
    private final boolean editMode;
    private final boolean editRiftContext;
    private final List<GridCellComponent> cells = new ArrayList<>();
    private final List<GridCellComponent> visibleCells = new ArrayList<>();
    private final List<PageCardComponent> visibleCards = new ArrayList<>();
    private final List<SectionFrame> sectionFrames = new ArrayList<>();
    private final List<PlacedRow> rows = new ArrayList<>();
    private final List<RowBreakMark> rowBreakMarks = new ArrayList<>();
    private final Map<StorageKey, Boolean> pageMatchCache = new HashMap<>();
    private final Set<GridCellComponent> searchMisses = new HashSet<>();
    private ItemStack copyFilterTarget = ItemStack.EMPTY;
    private String pageMatchQuery = "";
    private int contentInset;
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

    public static int computeMainBackgroundHeight(int height) {
        return height - computeTopMargin() - computeBottomMargin();
    }

    public static int computeMainBackgroundY() {
        return computeTopMargin();
    }

    private static int computeTopMargin() {
        // Quick Nav buttons sit in a row just above and below the container, so add more margin to make space for them.
        if (SKYBLOCKER_LOADED && isSkyblockerQuickNavEnabled()) {
            return EnhancedStorageConfig.overviewTopMargin + EnhancedStorageConfig.extraTopAndBottomMarginForQuickNav;
        }
        return EnhancedStorageConfig.overviewTopMargin;
    }

    private static int computeBottomMargin() {
        // Reserve room for the inventory panel, which sits directly below the page overview.
        int margin = EnhancedStorageConfig.overviewBottomMargin + INVENTORY_HEIGHT;

        // Quick Nav buttons sit in a row just above and below the container, so add more margin to make space for them.
        if (SKYBLOCKER_LOADED && isSkyblockerQuickNavEnabled()) {
            margin += EnhancedStorageConfig.extraTopAndBottomMarginForQuickNav - 5;
        }
        if (RRV_LOADED) {
            margin += EnhancedStorageConfig.extraBottomMarginForRecipeSearchBar;
        }
        return margin;
    }

    private static boolean isSkyblockerQuickNavEnabled() {
        try {
            return de.hysky.skyblocker.config.SkyblockerConfigManager.get().quickNav.enableQuickNav;
        } catch (NoClassDefFoundError e) {
            return false;
        }
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
                    EnhancedStorageConfig.cardSpacing, UNCACHED_CARD_HEIGHT);
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

    static boolean isLink(List<GridCellComponent> order, int i) {
        return i > 0 && i + 1 < order.size() && order.get(i) instanceof StackJoinComponent
                && order.get(i - 1) instanceof PageCardComponent && order.get(i + 1) instanceof PageCardComponent;
    }

    static int stackStart(List<GridCellComponent> order, int i) {
        while (isLink(order, i - 1)) i -= 2;
        return i;
    }

    static int stackEnd(List<GridCellComponent> order, int i) {
        while (isLink(order, i + 1)) i += 2;
        return i + 1;
    }

    private static List<LayoutEntry> cleanJoins(List<LayoutEntry> entries) {
        List<LayoutEntry> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            LayoutEntry entry = entries.get(i);
            if (entry instanceof LayoutEntry.StackJoin) {
                boolean linked = !result.isEmpty() && result.getLast() instanceof LayoutEntry.Page
                        && i + 1 < entries.size() && entries.get(i + 1) instanceof LayoutEntry.Page;
                if (!linked) continue;
            }
            result.add(entry);
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
        for (LayoutEntry entry : entries) {
            if (entry instanceof LayoutEntry.Page(StorageKey key)) keys.add(key);
        }
        return keys;
    }

    private static int freeSlots(StorageKey key) {
        return PageCache.getInstance().get(key).map(PageCache.CachedPage::emptySlots).orElse(0);
    }

    private static void addPanelTitle(SpriteComponent panel, String text, int color) {
        addPanelText(panel, 7, 3, text, color);
    }

    private static void addPanelText(SpriteComponent panel, int x, int y, String text, int color) {
        TextComponent component = new TextComponent(x, y, Component.literal(text), color);
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
        while (i < groups.size() && !isBoundary(groups.get(i).getFirst())
                && !(groups.get(i).getFirst() instanceof RowBreakComponent)) i++;
        return i;
    }

    private static boolean endsSectionAt(List<List<GridCellComponent>> groups, int index) {
        while (index < groups.size() && groups.get(index).getFirst() instanceof RowBreakComponent) index++;
        return index == groups.size() || isBoundary(groups.get(index).getFirst());
    }

    /**
     * Splits cells into flow items: linked pages become one group; joins themselves take no place.
     */
    private static List<List<GridCellComponent>> groupStacks(List<GridCellComponent> cells) {
        List<List<GridCellComponent>> groups = new ArrayList<>();
        int i = 0;
        while (i < cells.size()) {
            // Joins inside a stack are stepped over below; any other join links nothing.
            if (cells.get(i) instanceof StackJoinComponent) {
                i++;
                continue;
            }
            int end = stackEnd(cells, i);
            List<GridCellComponent> group = new ArrayList<>();
            for (int j = i; j < end; j += 2) group.add(cells.get(j));
            groups.add(group);
            i = end;
        }
        return groups;
    }

    private static RowFlow.Box boxOf(List<GridCellComponent> group, int spacing) {
        GridCellComponent first = group.getFirst();
        if (!(first instanceof PageCardComponent)) return new RowFlow.Box(first.getWidth(), 0);
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
                && TextUtils.stripString(name).contains(TextUtils.stripString(query));
    }

    private static boolean customNameMatches(StorageKey key, String query) {
        return PageNames.getInstance().get(key).map(name -> nameMatches(name, query)).orElse(false);
    }

    private static boolean isArrow(int key) {
        return key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN
                || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT;
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
        return CARD_BORDER * 2 + slotsAcross(key) * SLOT_SIZE;
    }

    private int cardHeightFor(StorageKey key) {
        if (!key.equals(liveKey) && PageCache.getInstance().get(key).isEmpty()) return UNCACHED_CARD_HEIGHT;
        return CARD_BORDER * 2 + titleAreaHeight + Math.max(1, Math.ceilDiv(slotCount(key), slotsAcross(key))) * SLOT_SIZE;
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

    public @Nullable PageCardComponent getOpenCard() {
        return openCard != null && visibleCards.contains(openCard) ? openCard : null;
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
        computeGeometry(width, entries);

        SpriteComponent mainBackground = buildMainPanel(screen, height);
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

    private void computeGeometry(int width, List<LayoutEntry> entries) {
        // Frames need room around the cards, so every row moves in to keep the columns aligned.
        boolean framed = entries.stream().anyMatch(LayoutEntry.Section.class::isInstance);
        this.contentInset = framed ? SECTION_INSET : 0;
        this.mainBackgroundY = computeMainBackgroundY();

        int available = width - EnhancedStorageConfig.horizontalMargin - INNER_PADDING * 2
                - SCROLLBAR_WIDTH - SCROLLBAR_GAP - contentInset * 2;
        this.maxSlotsAcross = Math.max(PageWidths.MIN_WIDTH, (available - CARD_BORDER * 2) / SLOT_SIZE);
        this.cardWidth = CARD_BORDER * 2 + Math.min(pageWidth(), maxSlotsAcross) * SLOT_SIZE;

        // "Pages per row" is measured in default-width pages, so narrow pages pack more per row.
        int pages = EnhancedStorageConfig.maxPagePerRow;
        int budget = pages * cardWidth + (pages - 1) * EnhancedStorageConfig.cardSpacing;
        setRowWidth(width, entries, Math.min(budget, available));

        this.navigationWidth = 0;
        if (EnhancedStorageConfig.navigationPanel == EnhancedStorageConfig.NavigationPanelSide.DISABLED) return;
        // The panel uses the free space beside the overlay; when there is too little, the rows narrow by what it needs.
        // The overlay is centred, so each pixel of missing space costs two pixels of width.
        while (navigationSpace() < NAV_MIN_WIDTH) {
            int before = rowContentWidth;
            setRowWidth(width, entries, before - (NAV_MIN_WIDTH - navigationSpace()) * 2);
            if (rowContentWidth >= before) break;
        }
        if (navigationSpace() >= NAV_MIN_WIDTH) {
            this.navigationWidth = Math.min(navigationSpace(), NAV_MAX_WIDTH);
        }
    }

    private void setRowWidth(int width, List<LayoutEntry> entries, int limit) {
        // Shrinks to the widest row so narrow pages don't leave empty overlay; never below one default page.
        this.rowContentWidth = Math.max(packedWidth(entries, limit), cardWidth);
        this.frameWidth = rowContentWidth + contentInset * 2;
        this.mainBackgroundWidth = frameWidth + INNER_PADDING * 2 + SCROLLBAR_WIDTH + SCROLLBAR_GAP;
        this.mainBackgroundX = (width / 2) - (mainBackgroundWidth / 2);
    }

    /**
     * Width of the widest row {@link RowFlow} produces at this limit, so the measured width and the real rows always agree.
     */
    private int packedWidth(List<LayoutEntry> entries, int limit) {
        int spacing = EnhancedStorageConfig.cardSpacing;
        int widest = 0;
        List<RowFlow.Box> segment = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            switch (entries.get(i)) {
                case LayoutEntry.Page(StorageKey key) -> {
                    int width = cardWidthFor(key);
                    int height = cardHeightFor(key);
                    // A linked page joins the stack box above it, exactly as groupStacks() does for the real cells.
                    boolean linked = i >= 2 && entries.get(i - 1) instanceof LayoutEntry.StackJoin
                            && entries.get(i - 2) instanceof LayoutEntry.Page;
                    if (linked) {
                        RowFlow.Box stack = segment.removeLast();
                        segment.add(new RowFlow.Box(Math.max(stack.width(), width), stack.height() + spacing + height));
                    } else {
                        segment.add(new RowFlow.Box(width, height));
                    }
                }
                case LayoutEntry.Spacer _ -> segment.add(new RowFlow.Box(cardWidth, 0));
                case LayoutEntry.StackJoin _ -> {
                }
                case LayoutEntry.Section _, LayoutEntry.SectionEnd _, LayoutEntry.RowBreak _ -> {
                    widest = Math.max(widest, widestRow(segment, limit));
                    segment.clear();
                }
            }
        }
        return Math.max(widest, widestRow(segment, limit));
    }

    private int navigationSpace() {
        return mainBackgroundX - EnhancedStorageConfig.horizontalMargin / 2 - NAV_GAP;
    }

    private SpriteComponent buildMainPanel(IScreen screen, int height) {
        SpriteComponent mainBackground = new SpriteComponent(mainBackgroundX, mainBackgroundY,
                mainBackgroundWidth, computeMainBackgroundHeight(height),
                OverlayTheme.sprite(OverlayTheme.MAIN_PANEL));
        screen.addComponent(mainBackground);
        return mainBackground;
    }

    private void buildNavigationPanel(IScreen screen, SpriteComponent mainBackground, List<LayoutEntry> entries) {
        if (navigationWidth == 0) return;
        boolean left = EnhancedStorageConfig.navigationPanel == EnhancedStorageConfig.NavigationPanelSide.LEFT;
        int x = left ? mainBackgroundX - NAV_GAP - navigationWidth : mainBackgroundX + mainBackgroundWidth + NAV_GAP;
        int height = mainBackground.getHeight();

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
        PageLayout.getInstance().setOrder(PageLayout.Context.of(riftContext), closeSections(cleanJoins(order)));
        for (LayoutEntry entry : order) {
            if (entry instanceof LayoutEntry.Page(StorageKey key)) PageOrder.getInstance().clear(key);
        }
        ProfileSession.getInstance().saveAll();
    }

    List<GridCellComponent> getCells() {
        return cells;
    }

    List<GridCellComponent> getVisibleCells() {
        return visibleCells;
    }

    List<LayoutEntry> currentEntries() {
        List<LayoutEntry> entries = new ArrayList<>();
        for (GridCellComponent cell : cells) {
            entries.add(cell.entry());
        }
        return entries;
    }

    int nextSpacerId() {
        int max = 0;
        for (GridCellComponent cell : cells) {
            if (cell.entry() instanceof LayoutEntry.Spacer(int id)) max = Math.max(max, id);
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

    int nextRowBreakId() {
        int max = 0;
        for (GridCellComponent cell : cells) {
            if (cell.entry() instanceof LayoutEntry.RowBreak(int id)) max = Math.max(max, id);
        }
        return max + 1;
    }

    int nextStackJoinId() {
        int max = 0;
        for (GridCellComponent cell : cells) {
            if (cell.entry() instanceof LayoutEntry.StackJoin(int id)) max = Math.max(max, id);
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

        int spacing = EnhancedStorageConfig.cardSpacing;
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
        return new Rect(viewport.x(), viewport.y() - INNER_PADDING, viewport.width(), viewport.height() + INNER_PADDING * 2);
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
            boolean hasRoom = last.bounds().right() + EnhancedStorageConfig.cardSpacing + cardWidth <= rowRight;
            return hasRoom && besideLast ? stackEnd(cells, cells.indexOf(last)) : -1;
        }
        return -1;
    }

    List<RowBreakMark> getRowBreakMarks() {
        return rowBreakMarks;
    }

    @Nullable RowBreakComponent rowBreakAt(double x, double y) {
        if (!Rect.contains(getPageOverviewBounds(), x, y)) return null;
        for (RowBreakMark mark : rowBreakMarks) {
            if (mark.bounds().contains(x, y)) return mark.cell();
        }
        return null;
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
            case SPACERS -> (int) entries.stream()
                    .filter(e -> e instanceof LayoutEntry.Spacer || e instanceof LayoutEntry.RowBreak).count();
            case STACKS -> (int) entries.stream().filter(LayoutEntry.StackJoin.class::isInstance).count();
        };
    }

    public void resetLayout(Set<ResetPart> parts) {
        List<LayoutEntry> entries = currentEntries();
        if (parts.contains(ResetPart.ORDER)) {
            Iterator<StorageKey> sorted = pageKeys(entries).stream().sorted(StorageKey.DISPLAY_ORDER).iterator();
            entries.replaceAll(entry -> entry instanceof LayoutEntry.Page ? new LayoutEntry.Page(sorted.next()) : entry);
        }
        entries.removeIf(entry -> switch (entry) {
            case LayoutEntry.Section _, LayoutEntry.SectionEnd _ -> parts.contains(ResetPart.SECTIONS);
            case LayoutEntry.Spacer _, LayoutEntry.RowBreak _ -> parts.contains(ResetPart.SPACERS);
            case LayoutEntry.StackJoin _ -> parts.contains(ResetPart.STACKS) || parts.contains(ResetPart.ORDER);
            case LayoutEntry.Page _ -> false;
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
                        cells.add(new SpacerCardComponent(cardWidth, UNCACHED_CARD_HEIGHT, spacer));
                case LayoutEntry.Page(StorageKey key) -> cells.add(buildPageCard(key, liveKey, liveRows));
                case LayoutEntry.Section section -> cells.add(buildSectionHeader(entries, i, section, liveKey));
                case LayoutEntry.SectionEnd _ -> cells.add(new SectionEndComponent(frameWidth, 0));
                case LayoutEntry.RowBreak rowBreak -> cells.add(new RowBreakComponent(rowBreak));
                case LayoutEntry.StackJoin join -> cells.add(new StackJoinComponent(join));
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
            if (entries.get(i) instanceof LayoutEntry.Page(StorageKey key)) {
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
                CARD_BORDER, titleAreaHeight, slotsAcross, SLOT_SIZE,
                actions.onCardClick()
        );
        if (isLive) this.openCard = pageCard;
        if (editMode && EnhancedStorageConfig.hideItemTooltipsInEditMode) pageCard.disableItemTooltips();
        return pageCard;
    }

    private void buildPageViewport(SpriteComponent mainBackground) {
        PageViewport viewport = new PageViewport(
                mainBackgroundWidth - INNER_PADDING * 2,
                mainBackground.getHeight() - INNER_PADDING * 2,
                EnhancedStorageConfig.cardSpacing, state::setScrollAmount);

        this.pageOverviewParentX = mainBackground.getTotalX() + INNER_PADDING;
        this.pageOverviewParentY = mainBackground.getTotalY() + INNER_PADDING;
        mainBackground.addWidget(viewport);
        viewport.setBackgroundPainter(this::paintSectionFrames);
        this.pageOverview = viewport;

        reflowRows(activeQuery());
        viewport.setScrollAmount(state.getScrollAmount());
    }

    // A remembered search could hide or dim pages the player is trying to arrange. So we set the search box to empty when entering edit mode and restore it when leaving.
    private String activeQuery() {
        return editMode ? "" : state.getSearchQuery();
    }

    private void buildBottomPanels(IScreen screen, int width, SpriteComponent mainBackground) {
        boolean showOverview = EnhancedStorageConfig.showStorageOverviewCard && !riftContext;

        int inventoryX = (width / 2) - (INVENTORY_WIDTH / 2);
        int overviewX = inventoryX - OVERVIEW_WIDTH;

        // On narrow screens, center the inventory + overview block under the main panel instead of the screen.
        if (showOverview && overviewX < mainBackgroundX) {
            int mainCenterX = mainBackgroundX + mainBackgroundWidth / 2;
            overviewX = mainCenterX - (INVENTORY_WIDTH + OVERVIEW_WIDTH) / 2;
            inventoryX = overviewX + OVERVIEW_WIDTH;
        }

        int panelY = mainBackgroundY + mainBackground.getHeight();

        String inventorySprite = editMode ? OverlayTheme.STORAGE_INVENTORY_FILLED : OverlayTheme.STORAGE_INVENTORY;
        SpriteComponent inventory = new SpriteComponent(inventoryX, panelY, INVENTORY_WIDTH, INVENTORY_HEIGHT,
                OverlayTheme.sprite(inventorySprite));
        screen.addComponent(inventory);
        this.inventoryPanel = inventory;
        this.inventoryBounds = new Rect(inventory.getTotalX(), inventory.getTotalY(),
                inventory.getWidth(), inventory.getHeight());

        SpriteComponent overview = null;
        if (showOverview) {
            overview = new SpriteComponent(overviewX, panelY, OVERVIEW_WIDTH, OVERVIEW_HEIGHT,
                    OverlayTheme.sprite(OverlayTheme.STORAGE_OVERVIEW));
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
            int y = IndexGrid.rowY(idx / IndexGrid.COLUMNS) + StorageSlotComponent.ITEM_INSET;
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
        this.searchBox.uilib$updateParentPosition(inventoryPanel.getTotalX(), inventoryPanel.getTotalY());
        screen.addWidget(this.searchBox);
    }

    /**
     * Buttons sit in a row to the right of the player inventory panel.
     */
    private void buildButtons(IScreen screen, int width, int height) {
        final int btnSize = 24;
        final int btnGap = 2;
        final int iconSize = 16;
        final int btnY = inventoryPanel.getTotalY() + 2;
        final int rowStartX = inventoryPanel.getTotalX() + INVENTORY_WIDTH + 2;
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
        }

        if (editMode) {
            int resetY = btnY + btnSize + btnGap;
            IconButtonComponent reset = new IconButtonComponent(
                    rowStartX, resetY, btnSize, btnSize,
                    getResetIcon(), iconSize,
                    List.of(Component.literal("Reset Layout").withStyle(s -> s.withColor(0x55FF55).withItalic(false)),
                            Component.literal("Choose what to restore to default")
                                    .withStyle(s -> s.withColor(0xFFAA00).withItalic(false))));
            this.resetButtonBounds = placeButton(screen, reset, rowStartX, resetY, btnSize, width, height);
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
        rowBreakMarks.clear();
        liveRowTop = -1;
        liveRowBottom = -1;

        if (visibleCells.isEmpty()) {
            pageOverview.addComponent(buildEmptyRow(query));
        }

        int spacing = EnhancedStorageConfig.cardSpacing;
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

            if (first instanceof RowBreakComponent) {
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
                        if (cell instanceof SpacerCardComponent spacer) spacer.setSlotHeight(spot.lineHeight());
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
            if (flow != null && end < groups.size() && groups.get(end).getFirst() instanceof RowBreakComponent rowBreak) {
                int left = contentInset + flow.width() + spacing;
                int right = contentInset + rowContentWidth;
                if (right - left >= SLOT_SIZE) {
                    rowBreakMarks.add(new RowBreakMark(rowBreak, row, left, right - left, flow.height()));
                }
            }
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
        for (GridCellComponent cell : visibleCells) {
            if (!(cell instanceof SectionHeaderComponent header) || !header.bounds().contains(mouseX, mouseY)) continue;
            if (!header.isCollapsed() || !copyFilterTarget.isEmpty() || header.isOverArrow(mouseX, mouseY)
                    || header.isOverGrip(mouseX, mouseY) || header.isOverTrash(mouseX, mouseY)) return;

            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(header.entry().name()));
            for (int i = cells.indexOf(header) + 1; i < cells.size() && !isBoundary(cells.get(i)); i++) {
                if (cells.get(i) instanceof PageCardComponent card) {
                    String name = PageNames.getInstance().get(card.getKey()).orElse(card.getKey().displayName());
                    lines.add(Component.literal(name).withStyle(ChatFormatting.GRAY));
                }
            }
            if (lines.size() > 1) graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
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
                & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER);
        int key = event.key();
        StorageKey target;
        if (mods == GLFW.GLFW_MOD_SHIFT && key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            // Consumed even without a page, so vanilla doesn't also swap the hovered slot with the hotbar.
            target = pageByNumber(key - GLFW.GLFW_KEY_1 + 1);
        } else if ((mods & ~GLFW.GLFW_MOD_ALT) == 0 && isArrow(key)) {
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
            case GLFW.GLFW_KEY_LEFT -> index > 0 ? visibleCards.get(index - 1).getKey() : null;
            case GLFW.GLFW_KEY_RIGHT -> index < visibleCards.size() - 1 ? visibleCards.get(index + 1).getKey() : null;
            default -> verticalNeighbour(current, key == GLFW.GLFW_KEY_UP);
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
        SPACERS("Spacers & row breaks"),
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

    record RowBreakMark(RowBreakComponent cell, EmptyComponent row, int x, int width, int height) {
        Rect bounds() {
            return new Rect(row.getTotalX() + x, row.getTotalY(), width, height);
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

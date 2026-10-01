package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.api.screen.IScreen;
import com.daqem.uilib.gui.component.EmptyComponent;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.component.text.TruncatedTextComponent;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.component.*;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.PageOrder;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.util.ItemModels;
import com.github.kdgaming0.enhancedstorage.util.ItemSearch;
import com.github.kdgaming0.enhancedstorage.util.TextUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
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
    private final List<PageCardComponent> pageCards = new ArrayList<>();
    private final List<PageCardComponent> visibleCards = new ArrayList<>();
    private final List<StorageKey> defaultOrder = new ArrayList<>();
    private final Map<StorageKey, Boolean> pageMatchCache = new HashMap<>();
    private String pageMatchQuery = "";
    private int liveRowTop = -1;
    private int liveRowBottom = -1;
    // Row-flow inputs captured by build() so applySearch() can re-pack rows without re-running build().
    private Font font;
    private int columns;
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
    private boolean searchDirty;
    private @Nullable Rect toolkitButtonBounds;
    private @Nullable Rect settingsButtonBounds;
    private @Nullable Rect themeButtonBounds;
    private @Nullable Rect inventoryBounds;
    private @Nullable Rect overviewBounds;

    public static int computeColumns(int width) {
        int cardWidth = CARD_BORDER * 2 + SLOTS_ACROSS * SLOT_SIZE;
        int maxAvailableWidth = width - EnhancedStorageConfig.horizontalMargin
                - INNER_PADDING * 2 - SCROLLBAR_WIDTH - SCROLLBAR_GAP;
        return Math.clamp((maxAvailableWidth + EnhancedStorageConfig.cardSpacing) / (cardWidth + EnhancedStorageConfig.cardSpacing),
                1, EnhancedStorageConfig.maxPagePerRow);
    }

    public static int computeMainBackgroundWidth(int width) {
        int cardWidth = CARD_BORDER * 2 + SLOTS_ACROSS * SLOT_SIZE;
        int columns = computeColumns(width);
        int rowContentWidth = columns * cardWidth + (columns - 1) * EnhancedStorageConfig.cardSpacing;
        return rowContentWidth + INNER_PADDING * 2 + SCROLLBAR_WIDTH + SCROLLBAR_GAP;
    }

    public static int computeMainBackgroundHeight(int height) {
        return height - computeTopMargin() - computeBottomMargin();
    }

    public static int computeMainBackgroundX(int width) {
        return (width / 2) - (computeMainBackgroundWidth(width) / 2);
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

    private static int cardHeightFor(StorageKey key, int titleAreaHeight) {
        return PageCache.getInstance().get(key)
                .map(page -> {
                    int rows = Math.max(1, Math.ceilDiv(page.items().size(), StorageOverlayLayout.SLOTS_ACROSS));
                    return StorageOverlayLayout.CARD_BORDER * 2 + titleAreaHeight + rows * StorageOverlayLayout.SLOT_SIZE;
                })
                .orElse(StorageOverlayLayout.UNCACHED_CARD_HEIGHT);
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

    private static ItemStack buildToolkitIcon() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL,
                Identifier.fromNamespaceAndPath("hypixel_skyblock",
                        "item/island_relevant/foraging_2/hunting_toolkit"));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Toolkits").withStyle(s -> s.withItalic(false)));
        return stack;
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

    public PageCardComponent getOpenCard() {
        return openCard;
    }

    public List<PageCardComponent> getPageCards() {
        return visibleCards;
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

    public @Nullable Rect getInventoryBounds() {
        return inventoryBounds;
    }

    /** Null when the Storage Overview card is hidden (Rift context or disabled in config). */
    public @Nullable Rect getOverviewBounds() {
        return overviewBounds;
    }

    public @Nullable Rect getPageOverviewBounds() {
        if (pageOverview == null) return null;
        return new Rect(pageOverview.getX(), pageOverview.getY(),
                pageOverview.getWidth(), pageOverview.getHeight());
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
        computeGeometry(width);

        SpriteComponent mainBackground = buildMainPanel(screen, height);
        buildPageCards(collectPageKeys(liveKey), liveKey, liveRows);
        buildPageViewport(mainBackground);

        buildBottomPanels(screen, width, mainBackground);
        addOverviewIndexItems(liveKey);
        addPanelTitle(inventoryPanel, "Inventory", OverlayTheme.titleColor());
        if (overviewPanel != null) {
            boolean overviewActive = liveKey != null && liveKey.type() == StorageKey.Type.STORAGE_INDEX;
            addPanelTitle(overviewPanel, "Storage Overview", overviewActive ? 0xFFFFD24A : OverlayTheme.titleColor());
        }

        ensureWarmupQueue();
        buildSearchBox(screen);
        buildButtons(screen, width, height);
    }

    private void resetForBuild(Font font, StorageOverlayState state, OverlayActions actions,
                               @Nullable StorageKey liveKey) {
        this.font = font;
        this.state = state;
        this.actions = actions;
        this.titleAreaHeight = font.lineHeight + 2;
        this.riftContext = liveKey != null && liveKey.type() == StorageKey.Type.RIFT;
        this.openCard = null;
        this.searchDirty = false;
        this.liveRowTop = -1;
        this.liveRowBottom = -1;
        this.pageCards.clear();
        this.visibleCards.clear();
        this.pageMatchCache.clear();
        this.pageMatchQuery = "";
        this.toolkitButtonBounds = null;
        this.settingsButtonBounds = null;
        this.themeButtonBounds = null;
    }

    private void computeGeometry(int width) {
        this.cardWidth = CARD_BORDER * 2 + SLOTS_ACROSS * SLOT_SIZE;
        this.columns = computeColumns(width);
        this.rowContentWidth = columns * cardWidth + (columns - 1) * EnhancedStorageConfig.cardSpacing;
        this.mainBackgroundX = computeMainBackgroundX(width);
        this.mainBackgroundY = computeMainBackgroundY();
        this.mainBackgroundWidth = computeMainBackgroundWidth(width);
    }

    private SpriteComponent buildMainPanel(IScreen screen, int height) {
        SpriteComponent mainBackground = new SpriteComponent(mainBackgroundX, mainBackgroundY,
                mainBackgroundWidth, computeMainBackgroundHeight(height),
                OverlayTheme.sprite(OverlayTheme.MAIN_PANEL));
        screen.addComponent(mainBackground);
        return mainBackground;
    }

    private List<StorageKey> collectPageKeys(@Nullable StorageKey liveKey) {
        List<StorageKey> keys = Stream.concat(
                        Stream.ofNullable(liveKey),
                        Stream.concat(
                                PageCache.getInstance().all().keySet().stream(),
                                PageCache.getInstance().allKnown().stream())).filter(Objects::nonNull)
                .filter(k -> k.type() != StorageKey.Type.STORAGE_INDEX)
                .filter(k -> riftContext == (k.type() == StorageKey.Type.RIFT))
                .distinct()
                .sorted(StorageKey.DISPLAY_ORDER)
                .toList();

        defaultOrder.clear();
        defaultOrder.addAll(keys);
        return applyCustomOrder(keys);
    }

    /** Builds every card once for the unfiltered list; search later picks which instances go into rows. */
    private void buildPageCards(List<StorageKey> pageKeys, @Nullable StorageKey liveKey, int liveRows) {
        for (StorageKey key : pageKeys) {
            boolean isLive = key.equals(liveKey);

            boolean cached = PageCache.getInstance().get(key).isPresent();
            List<ItemStack> items = isLive
                    ? List.of()
                    : PageCache.getInstance().get(key)
                    .map(PageCache.CachedPage::items).orElse(List.of());

            int cardHeight = isLive
                    ? CARD_BORDER * 2 + titleAreaHeight + (liveRows - 1) * SLOT_SIZE
                    : cardHeightFor(key, titleAreaHeight);

            PageCardComponent pageCard = new PageCardComponent(
                    0, 0,
                    cardWidth, cardHeight,
                    key, state,
                    items,
                    isLive || cached,
                    isLive,
                    CARD_BORDER, titleAreaHeight, SLOTS_ACROSS, SLOT_SIZE,
                    actions.onCardClick()
            );
            if (isLive) this.openCard = pageCard;
            this.pageCards.add(pageCard);
        }
    }

    private void buildPageViewport(SpriteComponent mainBackground) {
        PageViewport viewport = new PageViewport(
                mainBackgroundWidth - INNER_PADDING * 2,
                mainBackground.getHeight() - INNER_PADDING * 2,
                EnhancedStorageConfig.cardSpacing, state);

        this.pageOverviewParentX = mainBackground.getTotalX() + INNER_PADDING;
        this.pageOverviewParentY = mainBackground.getTotalY() + INNER_PADDING;
        mainBackground.addWidget(viewport);
        this.pageOverview = viewport;

        reflowRows(state.getSearchQuery());
        viewport.setScrollAmount(state.getScrollAmount());
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

        SpriteComponent inventory = new SpriteComponent(inventoryX, panelY, INVENTORY_WIDTH, INVENTORY_HEIGHT,
                OverlayTheme.sprite(OverlayTheme.STORAGE_INVENTORY));
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
            item.setTooltipEnabled(EnhancedStorageConfig.showItemTooltipsOnCachedItems);
            item.updateParentPosition(overview.getTotalX(), overview.getTotalY(),
                    overview.getWidth(), overview.getHeight());
            overview.addComponent(item);
        }
    }

    private static void addPanelTitle(SpriteComponent panel, String text, int color) {
        TextComponent title = new TextComponent(7, 3, Component.literal(text), color);
        title.updateParentPosition(panel.getTotalX(), panel.getTotalY(), panel.getWidth(), panel.getHeight());
        title.setDrawShadow(OverlayTheme.titleShadow());
        panel.addComponent(title);
    }

    private void ensureWarmupQueue() {
        if (warmupQueue != null) return;
        warmupQueue = new ArrayDeque<>();
        PageCache.getInstance().all().values().forEach(page -> warmupQueue.addAll(page.items()));
    }

    /** The search box instance survives rebuilds so typed text and focus are kept. */
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

    /** Buttons sit in a row to the right of the player inventory panel. */
    private void buildButtons(IScreen screen, int width, int height) {
        final int btnSize = 24;
        final int btnGap = 2;
        final int iconSize = 16;
        final int btnY = inventoryPanel.getTotalY() + 2;
        int btnX = inventoryPanel.getTotalX() + INVENTORY_WIDTH + 2;

        if (EnhancedStorageConfig.showToolkitButton && !riftContext) {
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
        }
    }

    private static Rect placeButton(IScreen screen, AbstractComponent button, int x, int y, int size,
                                    int screenWidth, int screenHeight) {
        button.updateParentPosition(0, 0, screenWidth, screenHeight);
        screen.addComponent(button);
        return new Rect(x, y, size, size);
    }

    private void applySearch(String query) {
        if (pageOverview == null) return;

        reflowRows(query);
        // Hiding cards shrinks the content, so an old scroll offset can now point past the end.
        pageOverview.setScrollAmount(pageOverview.scrollAmount());
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

        if (Rect.contains(settingsButtonBounds, x, y)) {
            actions.openSettings().run();
            return true;
        }
        if (Rect.contains(themeButtonBounds, x, y)) {
            OverlayTheme.cycle();
            actions.rebuild().run();
            return true;
        }
        return false;
    }

    /**
     * Packs the cards matching {@code query} into rows of {@link #columns}, replacing whatever the
     * scroll container held before. Card instances are reused; only the lightweight row wrappers
     * are allocated.
     */
    private void reflowRows(String query) {
        List<PageCardComponent> visible = visibleCards;
        visible.clear();
        for (PageCardComponent card : pageCards) {
            if (query.isBlank() || card.isLive() || pageMatches(card.getKey(), query)) {
                card.applySearch(query);
                visible.add(card);
            }
        }

        pageOverview.clearComponents();
        liveRowTop = -1;
        liveRowBottom = -1;

        if (visible.isEmpty()) {
            pageOverview.addComponent(buildEmptyRow(query));
        }

        int contentY = 0;
        for (int i = 0; i < visible.size(); i += columns) {
            List<PageCardComponent> rowCards = visible.subList(i, Math.min(i + columns, visible.size()));

            // Row height = tallest card in this row
            int rowHeight = rowCards.stream()
                    .mapToInt(PageCardComponent::getHeight)
                    .max()
                    .orElse(UNCACHED_CARD_HEIGHT);

            EmptyComponent row = new EmptyComponent(0, 0, rowContentWidth, rowHeight);
            for (int col = 0; col < rowCards.size(); col++) {
                PageCardComponent card = rowCards.get(col);
                card.setX(col * (cardWidth + EnhancedStorageConfig.cardSpacing));

                // Checks if the current row being build has the live storage page, if so get it's Y position in the scroll component
                if (card.isLive()) {
                    liveRowTop = contentY;
                    liveRowBottom = contentY + rowHeight;
                }
                row.addComponent(card);
            }
            pageOverview.addComponent(row);
            contentY += rowHeight + pageOverview.getContentSpacing();
        }

        // Rows were just re-parented, so push the container's position back down the tree.
        pageOverview.uilib$updateParentPosition(pageOverviewParentX, pageOverviewParentY);
        pageOverview.positionRows();
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

    public int defaultPositionOf(StorageKey key) {
        int i = defaultOrder.indexOf(key);
        return i < 0 ? -1 : i + 1;
    }
}

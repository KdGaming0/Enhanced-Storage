package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.api.screen.IScreen;
import com.daqem.uilib.gui.component.EmptyComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.component.text.TruncatedTextComponent;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.component.IconButtonComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.ItemButtonComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.TooltipItemComponent;
import com.github.kdgaming0.enhancedstorage.storage.StorageCache;
import com.github.kdgaming0.enhancedstorage.storage.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.StorageOrder;
import com.github.kdgaming0.enhancedstorage.util.ItemSearch;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.Consumer;
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
    private final List<PageCardComponent> pageCards = new ArrayList<>();
    private final List<StorageKey> defaultOrder = new ArrayList<>();
    private int liveRowTop = -1;
    private int liveRowBottom = -1;
    private int mainBackgroundX;
    private int mainBackgroundY;
    private int mainBackgroundWidth;
    private int mainBackgroundHeight;
    private PageCardComponent openCard;
    private SpriteComponent inventoryPanel;
    private SpriteComponent overviewPanel;
    private ScrollContainerWidget pageOverview;
    private EditBoxWidget searchBox;
    private ItemButtonComponent toolkitButton;
    private int @Nullable [] toolkitButtonBounds;
    private IconButtonComponent settingsButton;
    private int @Nullable [] settingsButtonBounds;
    private IconButtonComponent themeButton;
    private int @Nullable [] themeButtonBounds;

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
        int margin = EnhancedStorageConfig.overviewBottomMargin + 96; // 96 is the space for the inventory panel below the Storage overview panel, adding more will create spacing between the edge of the screen and the storage overview as a whole

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
        return StorageCache.getInstance().get(key)
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

    private static int getTitleTextColor() {
        return switch (EnhancedStorageConfig.backgroundType) {
            case LIGHT -> 0xFF000000; // vanilla black
            default -> 0xFFAAAAAA;
        };
    }

    private static boolean shouldDrawTitleShadow() {
        return EnhancedStorageConfig.backgroundType != EnhancedStorageConfig.BackgroundType.LIGHT;
    }

    private static List<StorageKey> applyCustomOrder(List<StorageKey> defaultOrder) {
        StorageOrder order = StorageOrder.getInstance();

        // Split into customs (with a requested position) and the rest (default flow).
        record Custom(StorageKey key, int pos, int defaultIndex) {}
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

    private Identifier getMainBackgroundTexture() {
        return switch (EnhancedStorageConfig.backgroundType) {
            case TRANSPARENT -> Identifier.fromNamespaceAndPath(MOD_ID, "transparent/main_panel_trans");
            case DARK -> Identifier.fromNamespaceAndPath(MOD_ID, "dark/main_panel_dark");
            default -> Identifier.fromNamespaceAndPath(MOD_ID, "light/main_panel");
        };
    }

    private Identifier getInventoryTexture() {
        return switch (EnhancedStorageConfig.backgroundType) {
            case TRANSPARENT -> Identifier.fromNamespaceAndPath(MOD_ID, "transparent/storage_inventory_trans");
            case DARK -> Identifier.fromNamespaceAndPath(MOD_ID, "dark/storage_inventory_dark");
            default -> Identifier.fromNamespaceAndPath(MOD_ID, "light/storage_inventory");
        };
    }

    private Identifier getOverviewTexture() {
        return switch (EnhancedStorageConfig.backgroundType) {
            case TRANSPARENT -> Identifier.fromNamespaceAndPath(MOD_ID, "transparent/storage_overview_trans");
            case DARK -> Identifier.fromNamespaceAndPath(MOD_ID, "dark/storage_overview_dark");
            default -> Identifier.fromNamespaceAndPath(MOD_ID, "light/storage_overview");
        };
    }

    public ItemButtonComponent getToolkitButton() {
        return toolkitButton;
    }

    public int @Nullable [] getToolkitButtonBounds() {
        return toolkitButtonBounds;
    }

    public IconButtonComponent getSettingsButton() {
        return settingsButton;
    }

    public int @Nullable [] getSettingsButtonBounds() {
        return settingsButtonBounds;
    }

    public IconButtonComponent getThemeButton() {
        return themeButton;
    }

    public int @Nullable [] getThemeButtonBounds() {
        return themeButtonBounds;
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

    public PageCardComponent getOpenCard() {
        return openCard;
    }

    public List<PageCardComponent> getPageCards() {
        return pageCards;
    }

    public SpriteComponent getInventoryPanel() {
        return inventoryPanel;
    }

    public SpriteComponent getOverviewPanel() {
        return overviewPanel;
    }

    public ScrollContainerWidget getPageOverview() {
        return pageOverview;
    }

    public EditBoxWidget getSearchBox() {
        return searchBox;
    }

    /**
     * Builds the full overlay onto the given screen.
     *
     * @param liveKey  the page backed by a real menu right now, or null in browse mode
     * @param liveRows row count of the real menu (ignored when liveKey == null)
     */
    public void build(IScreen screen, Font font, int width, int height, StorageOverlayState state,
                      @Nullable StorageKey liveKey, int liveRows, Consumer<StorageKey> onCardClick,
                      Runnable onSearchChanged, Consumer<String> onToolkitClick) {

        this.liveRowTop = -1;
        this.liveRowBottom = -1;
        this.pageCards.clear();
        this.toolkitButton = null;
        this.toolkitButtonBounds = null;
        this.settingsButton = null;
        this.settingsButtonBounds = null;
        this.themeButton = null;
        this.themeButtonBounds = null;

        int titleAreaHeight = font.lineHeight + 2;

        boolean riftContext = liveKey != null && liveKey.type() == StorageKey.Type.RIFT;

        List<StorageKey> pageKeys = Stream.concat(
                        Stream.ofNullable(liveKey),
                        Stream.concat(
                                StorageCache.getInstance().all().keySet().stream(),
                                StorageCache.getInstance().allKnown().stream())).filter(Objects::nonNull)
                .filter(k -> k.type() != StorageKey.Type.STORAGE_INDEX)
                .filter(k -> riftContext == (k.type() == StorageKey.Type.RIFT))
                .distinct()
                .sorted(StorageKey.DISPLAY_ORDER)
                .toList();

        this.defaultOrder.clear();
        this.defaultOrder.addAll(pageKeys);
        pageKeys = applyCustomOrder(pageKeys);

        String query = state.getSearchQuery();
        if (!query.isBlank()) {
            pageKeys = pageKeys.stream()
                    .filter(key -> key.equals(liveKey)
                            || StorageCache.getInstance().get(key)
                            .map(page -> ItemSearch.anyMatch(page.items(), query))
                            .orElse(false))
                    .toList();
        }

        int cardWidth = CARD_BORDER * 2 + SLOTS_ACROSS * SLOT_SIZE;

        int columns = computeColumns(width);
        int rowContentWidth = columns * cardWidth + (columns - 1) * EnhancedStorageConfig.cardSpacing;

        int mainBackgroundWidth = computeMainBackgroundWidth(width);
        int mainBackgroundHeight = computeMainBackgroundHeight(height);
        int mainBackgroundX = computeMainBackgroundX(width);
        int mainBackgroundY = computeMainBackgroundY();

        this.mainBackgroundX = mainBackgroundX;
        this.mainBackgroundY = mainBackgroundY;
        this.mainBackgroundWidth = mainBackgroundWidth;
        this.mainBackgroundHeight = mainBackgroundHeight;

        SpriteComponent mainBackground = new SpriteComponent(mainBackgroundX, mainBackgroundY, mainBackgroundWidth, mainBackgroundHeight, getMainBackgroundTexture());
        screen.addComponent(mainBackground);

        ScrollContainerWidget pageOverview = new ScrollContainerWidget(mainBackgroundWidth - INNER_PADDING * 2, mainBackgroundHeight - INNER_PADDING * 2, EnhancedStorageConfig.cardSpacing) {
            @Override
            public boolean isMouseOver(double x, double y) {
                // Only claim the scrollbar in automatic (getChildAt) dispatch. Everything else falls through so container slot logic can run.
                return this.visible && this.isOverScrollbar(x, y);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                if (updateScrolling(event)) return true;
                // Ignore clicks outside the visible viewport entirely
                if (event.x() < getX() || event.x() >= getX() + getWidth()
                        || event.y() < getY() || event.y() >= getY() + getHeight()) {
                    return false;
                }
                for (GuiEventListener child : children()) {
                    if (child.mouseClicked(event, doubleClick)) {
                        setFocused(child);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void setScrollAmount(double amount) {
                super.setScrollAmount(amount);
                state.setScrollAmount(scrollAmount());
            }

            @Override
            protected double scrollRate() {
                return EnhancedStorageConfig.overlayScrollSpeed;
            }
        };

        if (pageKeys.isEmpty()) {
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
                    textX, textY, rowContentWidth - textX, message, getTitleTextColor());
            emptyText.setDrawShadow(shouldDrawTitleShadow());

            EmptyComponent emptyRow = new EmptyComponent(0, 0, rowContentWidth, rowHeight);
            emptyRow.addComponent(emptyText);
            pageOverview.addComponent(emptyRow);
        }

        int contentY = 0;
        for (int i = 0; i < pageKeys.size(); i += columns) {
            List<StorageKey> rowKeys = pageKeys.subList(i, Math.min(i + columns, pageKeys.size()));

            // Row height = tallest card in this row
            int rowHeight = rowKeys.stream()
                    .mapToInt(key -> cardHeightForRow(key, liveKey, liveRows, titleAreaHeight))
                    .max()
                    .orElse(UNCACHED_CARD_HEIGHT);

            // Checks if the current row being build has the live storage page, if so get it's Y position in the scroll component
            if (liveKey != null && rowKeys.contains(liveKey)) {
                liveRowTop = contentY;
                liveRowBottom = contentY + rowHeight;
            }

            EmptyComponent row = new EmptyComponent(0, 0, rowContentWidth, rowHeight);
            for (int col = 0; col < rowKeys.size(); col++) {
                StorageKey key = rowKeys.get(col);

                boolean isLive = key.equals(liveKey);

                boolean cached = StorageCache.getInstance().get(key).isPresent();
                List<ItemStack> items = isLive
                        ? List.of()
                        : StorageCache.getInstance().get(key)
                          .map(StorageCache.CachedPage::items).orElse(List.of());

                int cardHeight = isLive
                        ? CARD_BORDER * 2 + titleAreaHeight + (liveRows - 1) * SLOT_SIZE
                        : cardHeightFor(key, titleAreaHeight);

                PageCardComponent pageCard = new PageCardComponent(
                        col * (cardWidth + EnhancedStorageConfig.cardSpacing), 0,
                        cardWidth, cardHeight,
                        key, state,
                        items,
                        isLive || cached,
                        isLive,
                        CARD_BORDER, titleAreaHeight, SLOTS_ACROSS, SLOT_SIZE,
                        onCardClick
                );
                if (isLive) this.openCard = pageCard;
                this.pageCards.add(pageCard);
                row.addComponent(pageCard);
            }
            pageOverview.addComponent(row);
            contentY += rowHeight + pageOverview.getContentSpacing();
        }

        pageOverview.uilib$updateParentPosition(mainBackground.getTotalX() + INNER_PADDING, mainBackground.getTotalY() + INNER_PADDING);
        mainBackground.addWidget(pageOverview);
        this.pageOverview = pageOverview;
        pageOverview.setScrollAmount(state.getScrollAmount());

        int mainBackgroundCenterX = mainBackgroundX + mainBackgroundWidth / 2;

        boolean showOverview = EnhancedStorageConfig.showStorageOverviewCard && !riftContext;

        int inventoryWidth = 176;
        int inventoryHeight = 96;
        int overviewWidth = 176;
        int overviewHeight = 85;
        int combinedWidth = showOverview ? inventoryWidth + overviewWidth : inventoryWidth;

        int inventoryX = (width / 2) - (inventoryWidth / 2);
        int overviewX = inventoryX - overviewWidth;

        // If the screen is too narrow, shift the inventory and overview to be centered under the mainBackground instead of the screen
        if (showOverview && overviewX < mainBackgroundX) {
            int blockX = mainBackgroundCenterX - (combinedWidth / 2);
            overviewX = blockX;
            inventoryX = blockX + overviewWidth;
        }

        int inventoryY = mainBackgroundY + mainBackgroundHeight;
        int overviewY = mainBackgroundY + mainBackgroundHeight;

        SpriteComponent inventory = new SpriteComponent(inventoryX, inventoryY, inventoryWidth, inventoryHeight, getInventoryTexture());
        screen.addComponent(inventory);
        this.inventoryPanel = inventory;

        SpriteComponent overview = null;
        if (showOverview) {
            overview = new SpriteComponent(overviewX, overviewY, overviewWidth, overviewHeight, getOverviewTexture());
            screen.addComponent(overview);
        }
        this.overviewPanel = overview;

        if (overview != null && (liveKey == null || liveKey.type() != StorageKey.Type.STORAGE_INDEX)) {
            SpriteComponent overviewPanel = overview;
            StorageCache.getInstance().get(new StorageKey(StorageKey.Type.STORAGE_INDEX, 0))
                    .map(StorageCache.CachedPage::items)
                    .ifPresent(items -> {
                        int startX = 7;
                        int[] rowYs = {14, 42};
                        for (int idx = 0; idx < items.size(); idx++) {
                            ItemStack stack = items.get(idx);
                            if (stack == null || stack.isEmpty()) continue;
                            int row = idx / 9;
                            int col = idx % 9;

                            int y = (row < rowYs.length) ? rowYs[row] : rowYs[rowYs.length - 1] + (row - rowYs.length + 1) * SLOT_SIZE;

                            int x = startX + col * SLOT_SIZE;
                            TooltipItemComponent item = new TooltipItemComponent(x + 1, y + 1, stack, true);
                            item.setTooltipEnabled(EnhancedStorageConfig.showItemTooltipsOnCachedItems);
                            item.updateParentPosition(overviewPanel.getTotalX(), overviewPanel.getTotalY(), overviewPanel.getWidth(), overviewPanel.getHeight());
                            overviewPanel.addComponent(item);
                        }
                    });
        }

        TextComponent inventoryTitle = new TextComponent(7, 3, Component.literal("Inventory"), getTitleTextColor());
        inventoryTitle.updateParentPosition(inventory.getTotalX(), inventory.getTotalY(), inventory.getWidth(), inventory.getHeight());
        inventoryTitle.setDrawShadow(shouldDrawTitleShadow());
        inventory.addComponent(inventoryTitle);

        if (overview != null) {
            boolean overviewActive = liveKey != null && liveKey.type() == StorageKey.Type.STORAGE_INDEX;
            int titleColor = overviewActive ? 0xFFFFD24A : getTitleTextColor();

            TextComponent storageOverviewTitle = new TextComponent(7, 3, Component.literal("Storage Overview"), titleColor);
            storageOverviewTitle.updateParentPosition(overview.getTotalX(), overview.getTotalY(), overview.getWidth(), overview.getHeight());
            storageOverviewTitle.setDrawShadow(shouldDrawTitleShadow());
            overview.addComponent(storageOverviewTitle);
        }

        EditBoxWidget searchBox = new EditBoxWidget(font, 70, 1, 100, 12, Component.literal("Search"));
        searchBox.setValue(state.getSearchQuery());
        searchBox.setResponder(text -> {
            if (text.equals(state.getSearchQuery())) return;
            state.setSearchQuery(text);
            onSearchChanged.run();
        });
        searchBox.uilib$updateParentPosition(inventory.getTotalX(), inventory.getTotalY());
        screen.addWidget(searchBox);
        this.searchBox = searchBox;

        // Buttons sit in a row to the right of the player inventory panel.
        final int btnSize = 24;
        final int btnGap = 2;
        final int btnY = inventory.getTotalY() + 2;
        int btnX = inventory.getTotalX() + inventoryWidth + 2;

        if (EnhancedStorageConfig.showToolkitButton && !riftContext) {
            ItemButtonComponent toolkit = new ItemButtonComponent(btnX, btnY, btnSize, btnSize, buildToolkitIcon());
            toolkit.updateParentPosition(0, 0, width, height);
            screen.addComponent(toolkit);
            this.toolkitButton = toolkit;
            this.toolkitButtonBounds = new int[]{btnX, btnY, btnSize, btnSize};
            btnX += btnSize + btnGap;
        }

        if (EnhancedStorageConfig.showSettingsButton && !riftContext) {
            int iconSize = 16;
            IconButtonComponent settings = new IconButtonComponent(
                    btnX, btnY, btnSize, btnSize,
                    getSettingsIcon(), iconSize,
                    List.of(Component.literal("Settings").withStyle(s -> s.withColor(0x55FF55).withItalic(false))));
            settings.updateParentPosition(0, 0, width, height);
            screen.addComponent(settings);
            this.settingsButton = settings;
            this.settingsButtonBounds = new int[]{btnX, btnY, btnSize, btnSize};
            btnX += btnSize + btnGap;
        }

        if (EnhancedStorageConfig.showThemeButton && !riftContext) {
            int iconSize = 16;
            IconButtonComponent theme = new IconButtonComponent(
                    btnX, btnY, btnSize, btnSize,
                    getThemeIcon(), iconSize,
                    List.of(
                            Component.literal("Theme: " + themeDisplayName())
                                    .withStyle(s -> s.withColor(0x55FF55).withItalic(false)),
                            Component.literal("Click to cycle")
                                    .withStyle(s -> s.withColor(0xFFAA00).withItalic(false))));
            theme.updateParentPosition(0, 0, width, height);
            screen.addComponent(theme);
            this.themeButton = theme;
            this.themeButtonBounds = new int[]{btnX, btnY, btnSize, btnSize};
            btnX += btnSize + btnGap;
        }
    }

    private int cardHeightForRow(StorageKey key, @Nullable StorageKey liveKey, int liveRows, int titleAreaHeight) {
        if (key.equals(liveKey)) {
            return CARD_BORDER * 2 + titleAreaHeight + (liveRows - 1) * SLOT_SIZE;
        }
        return cardHeightFor(key, titleAreaHeight);
    }

    private static Identifier getSettingsIcon() {
        return Identifier.fromNamespaceAndPath(MOD_ID, "icons/gear_icon");
    }

    private static Identifier getThemeIcon() {
        return switch (EnhancedStorageConfig.backgroundType) {
            case DARK -> Identifier.fromNamespaceAndPath(MOD_ID, "icons/icon_dark");
            case LIGHT -> Identifier.fromNamespaceAndPath(MOD_ID, "icons/icon_light");
            default -> Identifier.fromNamespaceAndPath(MOD_ID, "icons/icon_transparent");
        };
    }

    private static String themeDisplayName() {
        return switch (EnhancedStorageConfig.backgroundType) {
            case DARK -> "Dark";
            case LIGHT -> "Light";
            default -> "Transparent";
        };
    }

    public static void cycleTheme() {
        EnhancedStorageConfig.backgroundType = switch (EnhancedStorageConfig.backgroundType) {
            case TRANSPARENT -> EnhancedStorageConfig.BackgroundType.DARK;
            case DARK -> EnhancedStorageConfig.BackgroundType.LIGHT;
            case LIGHT -> EnhancedStorageConfig.BackgroundType.TRANSPARENT;
        };
        EnhancedStorageConfig.write(MOD_ID);
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
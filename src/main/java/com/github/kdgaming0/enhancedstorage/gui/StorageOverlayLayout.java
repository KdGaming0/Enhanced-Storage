package com.github.kdgaming0.enhancedstorage.gui;

import com.daqem.uilib.api.screen.IScreen;
import com.daqem.uilib.gui.component.EmptyComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.component.PageCardComponent;
import com.github.kdgaming0.enhancedstorage.gui.component.TooltipItemComponent;
import com.github.kdgaming0.enhancedstorage.storage.StorageCache;
import com.github.kdgaming0.enhancedstorage.storage.StorageKey;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.MOD_ID;


public class StorageOverlayLayout {

    public static final boolean RRV_LOADED = FabricLoader.getInstance().isModLoaded("rrv");

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

    public static final int SLOTS_ACROSS = 9;
    public static final int SLOT_SIZE = 18;
    public static final int CARD_BORDER = 3;
    public static final int CARD_SPACING = 3;
    public static final int INNER_PADDING = 6;
    public static final int SCROLLBAR_WIDTH = 6;
    public static final int SCROLLBAR_GAP = 4;
    public static final int UNCACHED_CARD_HEIGHT = 50;

    private int mainBackgroundX;
    private int mainBackgroundY;
    private int mainBackgroundWidth;
    private int mainBackgroundHeight;

    public int getMainBackgroundX()      { return mainBackgroundX; }
    public int getMainBackgroundY()      { return mainBackgroundY; }
    public int getMainBackgroundWidth()  { return mainBackgroundWidth; }
    public int getMainBackgroundHeight() { return mainBackgroundHeight; }

    private PageCardComponent openCard;
    private SpriteComponent inventoryPanel;
    private SpriteComponent overviewPanel;
    private ScrollContainerWidget pageOverview;

    public PageCardComponent getOpenCard() { return openCard; }
    public SpriteComponent getInventoryPanel() { return inventoryPanel; }
    public SpriteComponent getOverviewPanel() { return overviewPanel; }
    public ScrollContainerWidget getPageOverview() { return pageOverview; }

    /**
     * Builds the full overlay onto the given screen.
     *
     * @param liveKey  the page backed by a real menu right now, or null in browse mode
     * @param liveRows row count of the real menu (ignored when liveKey == null)
     */
    public void build(IScreen screen, Font font, int width, int height, StorageOverlayState state,
                      @Nullable StorageKey liveKey, int liveRows, Consumer<StorageKey> onCardClick) {

        int titleAreaHeight = font.lineHeight + 2;

        List<StorageKey> pageKeys = Stream.concat(
                        StorageCache.getInstance().all().keySet().stream(),
                        StorageCache.getInstance().allKnown().stream())
                .filter(k -> k.type() != StorageKey.Type.STORAGE_INDEX)
                .distinct()
                .sorted(StorageKey.DISPLAY_ORDER)
                .toList();

        int cardWidth = CARD_BORDER * 2 + SLOTS_ACROSS * SLOT_SIZE;

        int columns = computeColumns(width);
        int rowContentWidth = columns * cardWidth + (columns - 1) * CARD_SPACING;

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

        ScrollContainerWidget pageOverview = new ScrollContainerWidget(mainBackgroundWidth - INNER_PADDING * 2, mainBackgroundHeight - INNER_PADDING * 2, 3) {
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
        };

        if (pageKeys.isEmpty()) {
            TextComponent emptyText = new TextComponent(4, 4,
                    Component.literal("No storage pages cached yet. Open an Ender Chest or Backpack first."),
                    0xFFAAAAAA);
            emptyText.setDrawShadow(true);
            EmptyComponent emptyRow = new EmptyComponent(0, 0, rowContentWidth, titleAreaHeight + 8);
            emptyRow.addComponent(emptyText);
            pageOverview.addComponent(emptyRow);
        }

        for (int i = 0; i < pageKeys.size(); i += columns) {
            List<StorageKey> rowKeys = pageKeys.subList(i, Math.min(i + columns, pageKeys.size()));

            // Row height = tallest card in this row
            int rowHeight = rowKeys.stream()
                    .mapToInt(key -> cardHeightForRow(key, liveKey, liveRows, titleAreaHeight))
                    .max()
                    .orElse(UNCACHED_CARD_HEIGHT);

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
                        col * (cardWidth + CARD_SPACING), 0,
                        cardWidth, cardHeight,
                        key, state,
                        items,
                        isLive || cached,
                        isLive,
                        CARD_BORDER, titleAreaHeight, SLOTS_ACROSS, SLOT_SIZE,
                        onCardClick
                );
                if (isLive) this.openCard = pageCard;
                row.addComponent(pageCard);
            }
            pageOverview.addComponent(row);
        }

        pageOverview.uilib$updateParentPosition(mainBackground.getTotalX() + INNER_PADDING, mainBackground.getTotalY() + INNER_PADDING);
        mainBackground.addWidget(pageOverview);
        this.pageOverview = pageOverview;

        int mainBackgroundCenterX = mainBackgroundX + mainBackgroundWidth / 2;

        int inventoryWidth = 176;
        int inventoryHeight = 96;
        int overviewWidth = 176;
        int overviewHeight = 85;
        int combinedWidth = inventoryWidth + overviewWidth;

        int inventoryX = (width / 2) - (inventoryWidth / 2);
        int overviewX = inventoryX - overviewWidth;

        // If the screen is too narrow, shift the inventory and overview to be centered under the mainBackground instead of the screen
        if (overviewX < mainBackgroundX) {
            int blockX = mainBackgroundCenterX - (combinedWidth / 2);
            overviewX = blockX;
            inventoryX = blockX + overviewWidth;
        }

        int inventoryY = mainBackgroundY + mainBackgroundHeight;
        int overviewY = mainBackgroundY + mainBackgroundHeight;

        SpriteComponent inventory = new SpriteComponent(inventoryX, inventoryY, inventoryWidth, inventoryHeight, getInventoryTexture());
        screen.addComponent(inventory);
        this.inventoryPanel = inventory;

        SpriteComponent overview = new SpriteComponent(overviewX, overviewY, overviewWidth, overviewHeight, getOverviewTexture());
        screen.addComponent(overview);
        this.overviewPanel = overview;

        if (liveKey == null || liveKey.type() != StorageKey.Type.STORAGE_INDEX) {
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
                            item.updateParentPosition(overview.getTotalX(), overview.getTotalY(), overview.getWidth(), overview.getHeight());
                            overview.addComponent(item);
                        }
                    });
        }

        TextComponent inventoryTitle = new TextComponent(7, 3, Component.literal("Inventory"), 0xFFAAAAAA);
        inventoryTitle.updateParentPosition(inventory.getTotalX(), inventory.getTotalY(), inventory.getWidth(), inventory.getHeight());
        inventoryTitle.setDrawShadow(true);
        inventory.addComponent(inventoryTitle);

        TextComponent storageOverviewTitle = new TextComponent(7, 3, Component.literal("Storage Overview"), 0xFFAAAAAA);
        storageOverviewTitle.updateParentPosition(overview.getTotalX(), overview.getTotalY(), overview.getWidth(), overview.getHeight());
        storageOverviewTitle.setDrawShadow(true);
        overview.addComponent(storageOverviewTitle);

        EditBoxWidget searchBox = new EditBoxWidget(font, 70, 1, 100, 12, Component.literal("Search"));
        searchBox.uilib$updateParentPosition(inventory.getTotalX(), inventory.getTotalY());
        screen.addWidget(searchBox);
    }

    public static int computeColumns(int width) {
        int cardWidth = CARD_BORDER * 2 + SLOTS_ACROSS * SLOT_SIZE;
        int maxAvailableWidth = width - EnhancedStorageConfig.horizontalMargin
                - INNER_PADDING * 2 - SCROLLBAR_WIDTH - SCROLLBAR_GAP;
        return Math.clamp((maxAvailableWidth + CARD_SPACING) / (cardWidth + CARD_SPACING),
                1, EnhancedStorageConfig.maxPagePerRow);
    }

    public static int computeMainBackgroundWidth(int width) {
        int cardWidth = CARD_BORDER * 2 + SLOTS_ACROSS * SLOT_SIZE;
        int columns = computeColumns(width);
        int rowContentWidth = columns * cardWidth + (columns - 1) * CARD_SPACING;
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
        if (SkyblockerConfigManager.get().quickNav.enableQuickNav) {
            return EnhancedStorageConfig.overviewTopMargin + EnhancedStorageConfig.extraTopAndBottomMarginForQuickNav;
        }
        return EnhancedStorageConfig.overviewTopMargin;
    }

    private static int computeBottomMargin() {
        int margin = EnhancedStorageConfig.overviewBottomMargin + 96; // 96 is the space for the inventory panel below the Storage overview panel, adding more will create spacing between the edge of the screen and the storage overview as a whole

        // Quick Nav buttons sit in a row just above and below the container, so add more margin to make space for them.
        if (SkyblockerConfigManager.get().quickNav.enableQuickNav) {
            margin += EnhancedStorageConfig.extraTopAndBottomMarginForQuickNav - 5;
        }
        if (RRV_LOADED) {
            margin += EnhancedStorageConfig.extraBottomMarginForRecipeSearchBar;
        }
        return margin;
    }

    private int cardHeightForRow(StorageKey key, @Nullable StorageKey liveKey, int liveRows, int titleAreaHeight) {
        if (key.equals(liveKey)) {
            return CARD_BORDER * 2 + titleAreaHeight + (liveRows - 1) * SLOT_SIZE;
        }
        return cardHeightFor(key, titleAreaHeight);
    }

    private static int cardHeightFor(StorageKey key, int titleAreaHeight) {
        return StorageCache.getInstance().get(key)
                .map(page -> {
                    int rows = Math.max(1, Math.ceilDiv(page.items().size(), StorageOverlayLayout.SLOTS_ACROSS));
                    return StorageOverlayLayout.CARD_BORDER * 2 + titleAreaHeight + rows * StorageOverlayLayout.SLOT_SIZE;
                })
                .orElse(StorageOverlayLayout.UNCACHED_CARD_HEIGHT);
    }
}

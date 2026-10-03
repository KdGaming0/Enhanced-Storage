package com.github.kdgaming0.enhancedstorage.gui.component;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.OverlayTheme;
import com.github.kdgaming0.enhancedstorage.gui.Rect;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayState;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.util.ItemSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.MOD_ID;

/**
 * A clickable storage page card.
 */
public class PageCardComponent extends GridCellComponent {

    private final StorageKey key;
    private final int titleLocalX;
    private final int titleLocalY;
    private final int titleLocalWidth;
    private final int titleLocalHeight;

    private static final int TITLE_GAP = 2;
    private static final int EMPTY_LABEL_GAP = 6;
    private static final String FULL_TEXT = "Full";

    private final int slotGridX;
    private final int slotGridY;
    private final int slotsAcross;

    private final boolean cached;
    private final boolean live;
    private final List<ItemStack> items;
    private final List<StorageSlotComponent> slots = new ArrayList<>();
    private final TextComponent pageTitle;
    private @Nullable EditBoxWidget titleEditor;
    private @Nullable TextComponent emptyLabel;
    private @Nullable String shownEmptyText;

    Identifier idle = OverlayTheme.sprite(OverlayTheme.PAGE_CARD_IDLE);
    Identifier active = OverlayTheme.sprite(OverlayTheme.PAGE_CARD_ACTIVE);

    public PageCardComponent(int x, int y, int width, int height,
                             StorageKey key,
                             StorageOverlayState state,
                             List<ItemStack> items, int slotCount,
                             boolean cached, boolean live, boolean clickable,
                             int cardBorder, int titleAreaHeight,
                             int slotsAcross, int slotSize,
                             Consumer<StorageKey> onClick) {
        super(x, y, width, height);
        this.slotsAcross = slotsAcross;
        this.slotGridX = cardBorder;
        this.slotGridY = titleAreaHeight + TITLE_GAP;
        this.key = key;
        this.cached = cached;
        this.live = live;
        this.items = items;

        if (live) {
            SpriteComponent background = new SpriteComponent(0, 0, width, height, OverlayTheme.sprite(OverlayTheme.PAGE_CARD_ACTIVE));
            this.addComponent(background);
        } else {
            WidgetSprites idleSprites = new WidgetSprites(idle, idle, idle);
            WidgetSprites activeSprites = new WidgetSprites(active, active, active);

            PageCardButtonWidget background = new PageCardButtonWidget(
                    0, 0, width, height,
                    Component.empty(),
                    () -> state.isOpen(key) ? activeSprites : idleSprites,
                    btn -> onClick.accept(key)
            );
            // Makes pages not clickable when in edit mode.
            background.active = clickable;
            this.addWidget(background);
        }

        // Show the custom name if the user set one, otherwise the default.
        String displayName = PageNames.getInstance().get(key).orElse(key.displayName());

        int titleX = 3;
        int titleY = 3;
        var font = Minecraft.getInstance().font;
        // Truncate long titles to fit the card width.
        String shownTitle = truncate(font, displayName, width - titleX * 2);
        this.pageTitle = new TextComponent(titleX, titleY, Component.literal(shownTitle), OverlayTheme.titleColor());
        pageTitle.setDrawShadow(OverlayTheme.titleShadow());
        this.addComponent(pageTitle);

        int textWidth = font.width(shownTitle);
        this.titleLocalX = titleX;
        this.titleLocalY = titleY;
        this.titleLocalWidth = Math.min(textWidth, width - titleX * 2);
        this.titleLocalHeight = font.lineHeight;

        if (cached) {
            int fullRows = slotCount / slotsAcross;
            int remainder = slotCount % slotsAcross;

            if (fullRows > 0) addSlotGrid(slotGridX, slotGridY, slotsAcross * slotSize, fullRows * slotSize);
            if (remainder > 0) addSlotGrid(slotGridX, slotGridY + fullRows * slotSize, remainder * slotSize, slotSize);

            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                int slotRow = slotIndex / slotsAcross;
                int slotCol = slotIndex % slotsAcross;
                ItemStack stack = (!live && slotIndex < items.size()) ? items.get(slotIndex) : null;

                StorageSlotComponent slotComponent = new StorageSlotComponent(
                        slotGridX + slotCol * slotSize,
                        slotGridY + slotRow * slotSize,
                        slotSize, slotSize,
                        live ? null : SLOT_HIGHLIGHT_BACK,
                        live ? null : SLOT_HIGHLIGHT_FRONT,
                        stack);
                if (live) slotComponent.setHoverEnabled(false);
                this.slots.add(slotComponent);
                this.addComponent(slotComponent);
            }
            applySearch(state.getSearchQuery());

            if (EnhancedStorageConfig.showEmptySlotCount) {
                this.emptyLabel = new TextComponent(width - titleX, titleY, Component.empty(), OverlayTheme.secondaryTextColor());
                emptyLabel.setDrawShadow(OverlayTheme.titleShadow());
                this.addComponent(emptyLabel);
            }
        } else {
            Component label = Component.literal(clickable ? "Click To Open" : "Not Cached");
            int textX = (width - font.width(label)) / 2;
            int textY = (height - font.lineHeight) / 2;
            TextComponent pageInfo = new TextComponent(textX, textY, label, OverlayTheme.titleColor());
            pageInfo.setDrawShadow(OverlayTheme.titleShadow());
            this.addComponent(pageInfo);
        }
    }

    private static final Identifier SLOT_HIGHLIGHT_BACK =
            Identifier.fromNamespaceAndPath(MOD_ID, "light/storage_slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT =
            Identifier.fromNamespaceAndPath(MOD_ID, "light/storage_slot_highlight_front");

    private void addSlotGrid(int x, int y, int width, int height) {
        this.addComponent(new SpriteComponent(x, y, width, height, OverlayTheme.sprite(OverlayTheme.STORAGE_SLOT)));
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) end--;
        return text.substring(0, end) + ellipsis;
    }

    @Override
    public LayoutEntry entry() {
        return new LayoutEntry.Page(key);
    }

    public int slotGridScreenX() {
        return getTotalX() + slotGridX;
    }

    public int slotGridScreenY() {
        return getTotalY() + slotGridY;
    }

    public StorageKey getKey() {
        return key;
    }

    public boolean isCached() {
        return cached;
    }

    public boolean isLive() {
        return live;
    }

    public int slotsAcross() {
        return slotsAcross;
    }

    public ItemStack stackAt(double x, double y) {
        for (StorageSlotComponent slot : slots) {
            if (slot.contains(x, y)) return slot.getStack();
        }
        return ItemStack.EMPTY;
    }

    public void disableItemTooltips() {
        for (StorageSlotComponent slot : slots) {
            if (slot.getItem() != null) slot.getItem().setTooltipEnabled(false);
        }
    }

    // Apply the slot tint when searching
    public void applySearch(String query) {
        if (live || slots.isEmpty()) return;

        if (query.isBlank()) {
            for (StorageSlotComponent slot : slots) {
                slot.setSearchState(StorageSlotComponent.SearchState.NONE);
            }
            return;
        }

        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = (i < items.size()) ? items.get(i) : null;
            slots.get(i).setSearchState(ItemSearch.matches(stack, query)
                    ? StorageSlotComponent.SearchState.MATCH
                    : StorageSlotComponent.SearchState.NO_MATCH);
        }
    }

    // Re-read every frame: the live page's cache follows the player's clicks, and renaming hides the label.
    private void updateEmptyLabel() {
        if (emptyLabel == null) return;
        String text = titleEditor != null ? "" : PageCache.getInstance().get(key)
                .map(page -> emptyText(page.emptySlots()))
                .orElse("");
        if (text.equals(shownEmptyText)) return;
        shownEmptyText = text;

        int labelX = getWidth() - titleLocalX - Minecraft.getInstance().font.width(text);
        // A long custom title wins; the navigation panel's tooltip still shows the count.
        boolean fits = titleLocalX + titleLocalWidth + EMPTY_LABEL_GAP <= labelX;
        MutableComponent label = Component.literal(text);
        if (text.equals(FULL_TEXT)) label = label.withColor(OverlayTheme.fullTextColor() & 0xFFFFFF);
        emptyLabel.setText(fits ? label : Component.empty());
        emptyLabel.setX(labelX);
    }

    private static String emptyText(int empty) {
        return empty == 0 ? FULL_TEXT : empty + " empty";
    }

    // Makes the whole title area clickable, not just the text to make short titles easier to click.
    public Rect titleRowBounds() {
        return new Rect(getTotalX(), getTotalY() + titleLocalY, getWidth(), titleLocalHeight);
    }

    public boolean isOverTitleRow(double screenX, double screenY) {
        return titleRowBounds().contains(screenX, screenY);
    }

    /**
     * Replaces the title with a borderless text box in the same spot. The box text is the live title,
     * so the static title is blanked.
     */
    public EditBoxWidget beginTitleEdit(Font font) {
        String defaultName = key.displayName();
        EditBoxWidget box = new EditBoxWidget(font, titleLocalX, titleLocalY,
                getWidth() - titleLocalX * 2, font.lineHeight, Component.literal("Page name"));
        box.setBordered(false);
        box.setMaxLength(PageNames.MAX_NAME_LENGTH);
        box.setTextColor(OverlayTheme.titleColor());
        box.setResponder(text -> box.setSuggestion(text.isEmpty() ? defaultName : null));
        box.setValue(PageNames.getInstance().get(key).orElse(""));
        box.setSuggestion(box.getValue().isEmpty() ? defaultName : null);

        pageTitle.setText(Component.empty());
        this.titleEditor = box;
        this.addWidget(box);
        return box;
    }

    // Controls the rendering order
    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        // Make sure the title editor is positioned correctly as the card moves when the viewport scrolls.
        if (titleEditor != null) {
            titleEditor.uilib$updateParentPosition(getTotalX(), getTotalY());
        }
        updateEmptyLabel();

        // Render the background/button widget first
        for (IWidget widget : getWidgets()) {
            widget.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }

        // Render the Slots after
        this.extractRenderState(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);

        // Last render the items
        for (IComponent component : getComponents()) {
            component.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, getWidth(), getHeight());
        }
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
    }
}
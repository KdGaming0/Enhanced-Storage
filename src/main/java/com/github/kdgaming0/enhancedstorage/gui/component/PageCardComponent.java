package com.github.kdgaming0.enhancedstorage.gui.component;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.github.kdgaming0.enhancedstorage.gui.OverlayTheme;
import com.github.kdgaming0.enhancedstorage.gui.Rect;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayState;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.util.ItemSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
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
public class PageCardComponent extends AbstractComponent {

    private final StorageKey key;
    private final int titleLocalX;
    private final int titleLocalY;
    private final int titleLocalWidth;
    private final int titleLocalHeight;

    private static final int TITLE_GAP = 2;

    private final int slotGridX;
    private final int slotGridY;

    private final boolean cached;
    private final boolean live;
    private final List<ItemStack> items;
    private final List<StorageSlotComponent> slots = new ArrayList<>();
    private final TextComponent pageTitle;
    private @Nullable EditBoxWidget titleEditor;

    Identifier idle = OverlayTheme.sprite(OverlayTheme.PAGE_CARD_IDLE);
    Identifier active = OverlayTheme.sprite(OverlayTheme.PAGE_CARD_ACTIVE);

    public PageCardComponent(int x, int y, int width, int height,
                             StorageKey key,
                             StorageOverlayState state,
                             List<ItemStack> items,
                             boolean cached, boolean live, boolean clickable,
                             int cardBorder, int titleAreaHeight,
                             int slotsAcross, int slotSize,
                             Consumer<StorageKey> onClick) {
        super(x, y, width, height);
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
        this.pageTitle = new TextComponent(titleX, titleY, Component.literal(displayName), OverlayTheme.titleColor());
        pageTitle.setDrawShadow(OverlayTheme.titleShadow());
        this.addComponent(pageTitle);

        var font = Minecraft.getInstance().font;
        int textWidth = font.width(displayName);
        this.titleLocalX = titleX;
        this.titleLocalY = titleY;
        this.titleLocalWidth = Math.min(textWidth, width - titleX * 2);
        this.titleLocalHeight = font.lineHeight;

        if (cached) {
            int totalSlots = live ? ((height - cardBorder * 2 - titleAreaHeight) / slotSize) * slotsAcross : items.size();
            int pageRows = Math.max(1, Math.ceilDiv(totalSlots, slotsAcross));

            // The whole slot grid is one tiled sprite (18x18 tile mcmeta) — one draw call
            // instead of one per slot.
            SpriteComponent slotGrid = new SpriteComponent(
                    slotGridX, slotGridY,
                    slotsAcross * slotSize, pageRows * slotSize,
                    OverlayTheme.sprite(OverlayTheme.STORAGE_SLOT));
            this.addComponent(slotGrid);

            for (int slotRow = 0; slotRow < pageRows; slotRow++) {
                for (int slotCol = 0; slotCol < slotsAcross; slotCol++) {
                    int slotIndex = slotRow * slotsAcross + slotCol;
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
            }
            applySearch(state.getSearchQuery());
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

    public Rect bounds() {
        return new Rect(getTotalX(), getTotalY(), getWidth(), getHeight());
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

    public boolean isOverTitle(double screenX, double screenY) {
        int absX = getTotalX() + titleLocalX;
        int absY = getTotalY() + titleLocalY;
        return screenX >= absX && screenX < absX + titleLocalWidth
                && screenY >= absY && screenY < absY + titleLocalHeight;
    }


    // Makes the whole title area clickable, not just the text to make short titles easier to click.
    public boolean isOverTitleRow(double screenX, double screenY) {
        int absX = getTotalX();
        int absY = getTotalY() + titleLocalY;
        return screenX >= absX && screenX < absX + getWidth()
                && screenY >= absY && screenY < absY + titleLocalHeight;
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
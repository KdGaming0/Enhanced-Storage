package com.github.kdgaming0.enhancedstorage.gui.component;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayState;
import com.github.kdgaming0.enhancedstorage.storage.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.StorageNames;
import com.github.kdgaming0.enhancedstorage.util.ItemSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.MOD_ID;

/**
 * A clickable storage page card.
 */
public class PageCardComponent extends AbstractComponent {

    private static final WidgetSprites CARD_SPRITES = new WidgetSprites(
            getPageCardIdleTexture(),
            getPageCardIdleTexture(),
            getPageCardIdleTexture()
    );

    private static final WidgetSprites CARD_ACTIVE_SPRITES = new WidgetSprites(
            getPageCardActiveTexture(),
            getPageCardActiveTexture(),
            getPageCardActiveTexture()
    );

    private final StorageKey key;
    private final int titleLocalX;
    private final int titleLocalY;
    private final int titleLocalWidth;
    private final int titleLocalHeight;

    public PageCardComponent(int x, int y, int width, int height,
                             StorageKey key,
                             StorageOverlayState state,
                             List<ItemStack> items,
                             boolean cached, boolean live,
                             int cardBorder, int titleAreaHeight,
                             int slotsAcross, int slotSize,
                             Consumer<StorageKey> onClick) {
        super(x, y, width, height);
        this.key = key;

        String searchQuery = (state == null) ? "" : state.getSearchQuery();

        if (live) {
            SpriteComponent background = new SpriteComponent(0, 0, width, height, getPageCardActiveTexture());
            this.addComponent(background);
        } else {
            PageCardButtonWidget background = new PageCardButtonWidget(
                    0, 0, width, height,
                    Component.empty(),
                    () -> state.isOpen(key) ? CARD_ACTIVE_SPRITES : CARD_SPRITES,
                    btn -> onClick.accept(key)
            );
            this.addWidget(background);
        }

        // Show the custom name if the user set one, otherwise the default.
        String displayName = StorageNames.getInstance().get(key).orElse(key.displayName());

        int titleX = 3;
        int titleY = 3;
        TextComponent pageTitle = new TextComponent(titleX, titleY, Component.literal(displayName), 0xFFAAAAAA);
        pageTitle.setDrawShadow(true);
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

            for (int slotRow = 0; slotRow < pageRows; slotRow++) {
                for (int slotCol = 0; slotCol < slotsAcross; slotCol++) {
                    int slotIndex = slotRow * slotsAcross + slotCol;
                    ItemStack stack = (!live && slotIndex < items.size()) ? items.get(slotIndex) : null;

                    StorageSlotComponent slotComponent = new StorageSlotComponent(
                            cardBorder + slotCol * slotSize,
                            (titleAreaHeight + 2) + slotRow * slotSize,
                            slotSize, slotSize,
                            getStorageSlotTexture(),
                            live ? null : getStorageSlotHighlightBackTexture(),
                            live ? null : getStorageSlotHighlightFrontTexture(),
                            stack);
                    if (live) slotComponent.setHoverEnabled(false);
                    if (!live && !searchQuery.isBlank()) {
                        slotComponent.setSearchState(
                                ItemSearch.matches(stack, searchQuery)
                                        ? StorageSlotComponent.SearchState.MATCH
                                        : StorageSlotComponent.SearchState.NO_MATCH);
                    }
                    this.addComponent(slotComponent);
                }
            }
        } else {
            Component label = Component.literal("Click To Open");
            int textX = (width - font.width(label)) / 2;
            int textY = (height - font.lineHeight) / 2;
            TextComponent pageInfo = new TextComponent(textX, textY, label, 0xFFAAAAAA);
            pageInfo.setDrawShadow(true);
            this.addComponent(pageInfo);
        }
    }

    private static Identifier getPageCardIdleTexture() {
        return switch (EnhancedStorageConfig.backgroundType) {
            case TRANSPARENT -> Identifier.fromNamespaceAndPath(MOD_ID, "transparent/page_card_idle_trans");
            case DARK -> Identifier.fromNamespaceAndPath(MOD_ID, "dark/page_card_idle_dark");
            default -> Identifier.fromNamespaceAndPath(MOD_ID, "light/page_card_idle");
        };
    }

    private static Identifier getPageCardActiveTexture() {
        return switch (EnhancedStorageConfig.backgroundType) {
            case TRANSPARENT -> Identifier.fromNamespaceAndPath(MOD_ID, "transparent/page_card_active_trans");
            case DARK -> Identifier.fromNamespaceAndPath(MOD_ID, "dark/page_card_active_dark");
            default -> Identifier.fromNamespaceAndPath(MOD_ID, "light/page_card_active");
        };
    }

    private static Identifier getStorageSlotHighlightFrontTexture() {
        return Identifier.fromNamespaceAndPath(MOD_ID, "light/storage_slot_highlight_front");
    }

    private static Identifier getStorageSlotHighlightBackTexture() {
        return Identifier.fromNamespaceAndPath(MOD_ID, "light/storage_slot_highlight_back");
    }

    private Identifier getStorageSlotTexture() {
        return switch (EnhancedStorageConfig.backgroundType) {
            case TRANSPARENT -> Identifier.fromNamespaceAndPath(MOD_ID, "transparent/storage_slot_trans");
            case DARK -> Identifier.fromNamespaceAndPath(MOD_ID, "dark/storage_slot_dark");
            default -> Identifier.fromNamespaceAndPath(MOD_ID, "light/storage_slot");
        };
    }

    public StorageKey getKey() {
        return key;
    }

    public boolean isOverTitle(double screenX, double screenY) {
        int absX = getTotalX() + titleLocalX;
        int absY = getTotalY() + titleLocalY;
        return screenX >= absX && screenX < absX + titleLocalWidth
                && screenY >= absY && screenY < absY + titleLocalHeight;
    }

    public int getTitleScreenX() {
        return getTotalX() + titleLocalX;
    }

    public int getTitleScreenY() {
        return getTotalY() + titleLocalY;
    }

    // Controls the rendering order
    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
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
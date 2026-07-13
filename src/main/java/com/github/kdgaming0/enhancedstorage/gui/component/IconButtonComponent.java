package com.github.kdgaming0.enhancedstorage.gui.component;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.sprite.SpriteComponent;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.MOD_ID;

/**
 * A clickable button that shows a sprite icon on a nine-slice card background.
 */
public class IconButtonComponent extends AbstractComponent {

    private final List<Component> tooltip;

    public IconButtonComponent(int x, int y, int width, int height, Identifier icon, int iconSize, List<Component> tooltip) {
        super(x, y, width, height);
        this.tooltip = tooltip;

        // Nine-slice background, scaled to the button size (matches ItemButtonComponent).
        SpriteComponent background = new SpriteComponent(0, 0, width, height, getIdleTexture());
        this.addComponent(background);

        // Center the icon on the background.
        int iconX = (width - iconSize) / 2;
        int iconY = (height - iconSize) / 2;
        SpriteComponent iconSprite = new SpriteComponent(iconX, iconY, iconSize, iconSize, icon);
        this.addComponent(iconSprite);
    }

    private static Identifier getIdleTexture() {
        return switch (EnhancedStorageConfig.backgroundType) {
            case TRANSPARENT -> Identifier.fromNamespaceAndPath(MOD_ID, "transparent/page_card_idle_trans");
            case DARK -> Identifier.fromNamespaceAndPath(MOD_ID, "dark/page_card_idle_dark");
            default -> Identifier.fromNamespaceAndPath(MOD_ID, "light/page_card_idle");
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {

    }

    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, int parentWidth, int parentHeight) {
        super.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);

        boolean hovered = mouseX >= getTotalX() && mouseX < getTotalX() + getWidth()
                && mouseY >= getTotalY() && mouseY < getTotalY() + getHeight();
        if (hovered && tooltip != null && !tooltip.isEmpty()) {
            guiGraphics.setTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    tooltip,
                    java.util.Optional.empty(),
                    mouseX, mouseY
            );
        }
    }
}
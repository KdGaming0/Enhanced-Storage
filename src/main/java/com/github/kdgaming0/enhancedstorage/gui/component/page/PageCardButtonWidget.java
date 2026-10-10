package com.github.kdgaming0.enhancedstorage.gui.component.page;

import com.daqem.uilib.gui.widget.ButtonWidget;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

/**
 * ButtonWidget with themed sprites resolved at draw time and a theme-aware label shadow.
 */
public class PageCardButtonWidget extends ButtonWidget {

    private final Supplier<WidgetSprites> spriteSupplier;

    public PageCardButtonWidget(int x, int y, int width, int height,
                                Component message,
                                Supplier<WidgetSprites> spriteSupplier,
                                OnPress onPress) {
        super(x, y, width, height, message, onPress);
        this.spriteSupplier = spriteSupplier;

    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        WidgetSprites sprites = spriteSupplier.get();
        guiGraphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                sprites.get(this.active, this.isHoveredOrFocused()),
                this.getX(), this.getY(),
                this.getWidth(), this.getHeight(),
                ARGB.white(this.alpha)
        );

        // Drawn manually instead of extractDefaultLabel so the shadow can follow the theme.
        Font font = Minecraft.getInstance().font;
        Component message = this.getMessage();
        int textX = this.getX() + (this.getWidth() - font.width(message)) / 2;
        int textY = this.getY() + (this.getHeight() - font.lineHeight) / 2 + 1;
        guiGraphics.text(font, message, textX, textY, ARGB.white(this.alpha), OverlayStyle.titleShadow());
    }
}

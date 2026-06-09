package com.github.kdgaming0.enhancedstorage.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@SuppressWarnings("unused")
@Mixin(Screen.class)
public interface ScreenAccessor {
    @Invoker("addWidget")
    <T extends GuiEventListener & NarratableEntry> T es$addWidget(T widget);

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T es$addRenderableWidget(T widget);

    @Invoker("removeWidget")
    void es$removeWidget(GuiEventListener widget);
}

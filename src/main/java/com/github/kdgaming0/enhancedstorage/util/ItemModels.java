package com.github.kdgaming0.enhancedstorage.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ItemModels {

    private ItemModels() {
    }

    /**
     * Storage index icons carry an {@code item_model} baked from the texture pack that was loaded
     * when we captured them. Once that pack is gone the id resolves to the missing model, so put the
     * item's own default model back and let the icon render as the plain vanilla item. Re-enabling
     * the pack makes the baked id resolve again, so this heals by itself.
     *
     * <p>Restoring the default rather than removing the component matters: every item carries an
     * {@code item_model} in its prototype, so removing it patches the component to absent, which
     * renders nothing and breaks mods that assume every stack has one (RRV's namespace tooltip).
     */
    public static ItemStack withResolvableModel(ItemStack stack) {
        Identifier model = stack.get(DataComponents.ITEM_MODEL);
        if (model == null) return stack;

        ModelManager models = Minecraft.getInstance().getModelManager();
        if (!(models.getItemModel(model) instanceof MissingItemModel)) return stack;

        Identifier defaultModel = stack.getPrototype().get(DataComponents.ITEM_MODEL);
        if (defaultModel == null || defaultModel.equals(model)) return stack;

        ItemStack fallback = stack.copy();
        fallback.set(DataComponents.ITEM_MODEL, defaultModel);
        return fallback;
    }
}

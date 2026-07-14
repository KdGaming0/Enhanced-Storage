package com.github.kdgaming0.enhancedstorage.compat;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Optional integration with Catharsis (the mod behind {@code .cats} SkyBlock texture packs).
 *
 * <p>Catharsis picks an item's pack model either from the stack itself (SkyBlock id in
 * {@code custom_data}) or, for stacks that carry no id at all — the Ender Chest / Backpack icons on
 * the storage index — from a per-slot definition of the container screen that is currently open.
 * That second route only fires while vanilla renders the real slots, so our cached copies of those
 * icons would fall back to the plain vanilla item. Asking Catharsis for the slot's model while the
 * index screen is open and baking it into the cached copy as {@code item_model} makes the cached
 * icon render the same way through any path, including after a restart.
 *
 * <p>Reflective on purpose: Catharsis is not a compile dependency and must stay entirely optional.
 */
public final class CatharsisCompat {

    private static final String GUI_DEFINITIONS = "me.owdding.catharsis.features.gui.definitions.GuiDefinitions";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("catharsis");

    private static Method getSlot;
    private static boolean lookupDone;

    private CatharsisCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * The model Catharsis would draw for the given slot of the currently open container,
     * or null when Catharsis is absent, the pack defines no model for that slot, or the
     * internals we reflect on have moved.
     */
    public static @Nullable Identifier getSlotModel(int slotIndex) {
        Method method = lookup();
        if (method == null) return null;

        try {
            return (Identifier) method.invoke(null, slotIndex);
        } catch (ReflectiveOperationException | ClassCastException e) {
            getSlot = null;
            EnhancedStorage.LOGGER.warn("Catharsis slot model lookup failed; cached storage index icons will use vanilla textures", e);
            return null;
        }
    }

    private static @Nullable Method lookup() {
        if (!LOADED) return null;
        if (lookupDone) return getSlot;
        lookupDone = true;

        try {
            getSlot = Class.forName(GUI_DEFINITIONS).getMethod("getSlot", int.class);
        } catch (ReflectiveOperationException e) {
            EnhancedStorage.LOGGER.warn("Catharsis is installed but {}#getSlot(int) is missing; cached storage index icons will use vanilla textures", GUI_DEFINITIONS);
        }
        return getSlot;
    }
}

package com.github.kdgaming0.enhancedstorage.compat;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.screen.StorageContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime bridge for the SkyHanni compat mixins (mixin/compat/SkyHanni*Mixin).
 *
 * SkyHanni gates its chest features (Chest Value etc.) on the open screen being
 * the vanilla ContainerScreen. StorageContainerScreen extends uilib's container
 * screen instead, so those checks fail even though the ChestMenu underneath is
 * untouched. The mixins OR our screen into those checks and hand SkyHanni the
 * real container slots.
 *
 * Every entry point is wrapped so a SkyHanni-side change degrades to a single
 * logged warning instead of a crash.
 */
public final class SkyHanniCompat {

    private static final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();

    private SkyHanniCompat() {}

    /** true when the currently open screen is our storage screen. */
    public static boolean isStorageScreenOpen() {
        try {
            return Minecraft.getInstance().screen instanceof StorageContainerScreen;
        } catch (Throwable t) {
            reportFailure("isStorageScreenOpen", t);
            return false;
        }
    }

    /**
     * Container (non-player-inventory) slots of the open storage screen,
     * mirroring what SkyHanni's InventoryUtils.getItemsInOpenChestWithNull()
     * returns for a vanilla chest screen. Returns null when the storage screen
     * is not open (or on failure) so the caller keeps SkyHanni's original
     * behavior.
     */
    public static List<Slot> containerSlotsOfOpenStorageScreen() {
        try {
            Screen screen = Minecraft.getInstance().screen;
            if (!(screen instanceof StorageContainerScreen storageScreen)) {
                return null;
            }
            List<Slot> slots = new ArrayList<>();
            for (Slot slot : storageScreen.getMenu().slots) {
                if (!(slot.container instanceof Inventory)) {
                    slots.add(slot);
                }
            }
            return slots;
        } catch (Throwable t) {
            reportFailure("containerSlotsOfOpenStorageScreen", t);
            return null;
        }
    }

    /** Logs a hook failure once per session instead of spamming or crashing. */
    public static void reportFailure(String hook, Throwable t) {
        if (reportedFailures.add(hook)) {
            EnhancedStorage.LOGGER.warn(
                    "SkyHanni compat hook '{}' failed; disabling it for this session. "
                            + "SkyHanni chest features may not show on Enhanced Storage screens. "
                            + "This usually means a SkyHanni update changed its internals.",
                    hook, t);
        }
    }
}

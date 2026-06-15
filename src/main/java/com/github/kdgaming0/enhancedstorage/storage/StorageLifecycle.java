package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.HypixelNetworking;
import net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.azureaaron.hmapi.utils.Utils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages feature lifecycle: server detection, snapshot persistence, and overlay creation.
 */
public final class StorageLifecycle {

    private static final AtomicReference<StorageSnapshotStorage> STORAGE_REF = new AtomicReference<>();
    private static volatile String cachedProfileId = "unknown";
    private static volatile boolean onSkyBlock = false;

    private StorageLifecycle() {
    }

    public static void init() {
        Object2IntOpenHashMap<net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<HypixelS2CPacket>> events =
                new Object2IntOpenHashMap<>();
        events.put(LocationUpdateS2CPacket.ID, 1);
        HypixelNetworking.registerToEvents(events);
        HypixelPacketEvents.LOCATION_UPDATE.register(StorageLifecycle::onLocationUpdate);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Profile ID uses the Minecraft account UUID, not the Hypixel profile UUID.
            // Players with multiple SkyBlock profiles share one snapshot file per MC account.
            cachedProfileId = resolveProfileId();
            StorageSnapshotStorage storage = new StorageSnapshotStorage();
            storage.load(cachedProfileId, StorageData.INSTANCE);
            STORAGE_REF.set(storage);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> save());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> save());
    }

    private static void onLocationUpdate(HypixelS2CPacket packet) {
        if (packet instanceof LocationUpdateS2CPacket loc) {
            onSkyBlock = loc.serverType()
                    .map(t -> t.equalsIgnoreCase("SKYBLOCK"))
                    .orElse(false);
        }
    }

    public static boolean isFeatureEnabled() {
        return EnhancedStorageConfig.enableStorageOverlay
                && Utils.isOnHypixel()
                && onSkyBlock;
    }

    public static @Nullable StorageOverlay createOverlay(AbstractContainerScreen<?> screen) {
        if (!isFeatureEnabled()) {
            StorageOverlay.destroyActive();
            return null;
        }

        String rawTitle = screen.getTitle().getString();
        Optional<StorageTitleParser.ParsedTitle> parsed = StorageTitleParser.parse(rawTitle);
        if (parsed.isEmpty()) {
            StorageOverlay.destroyActive();
            return null;
        }

        if (parsed.get().isOverview()) {
            rememberOverview(screen);
            return StorageOverlay.createOrAttach(screen, null);
        }

        StoragePage page = parsed.get().page();
        if (page == null) {
            StorageOverlay.destroyActive();
            return null;
        }

        rememberPage(screen, page, rawTitle);
        return StorageOverlay.createOrAttach(screen, page);
    }

    public static void rememberPage(AbstractContainerScreen<?> screen, StoragePage page, String rawTitle) {
        List<ItemStack> stacks = collectContainerStacks(screen);
        if (stacks.isEmpty()) return;
        StorageData.INSTANCE.updateInventory(page, rawTitle, new VirtualInventory(stacks));
        StorageData.INSTANCE.markDirty();
    }

    public static void rememberOverview(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue;
            StoragePage page = StoragePage.fromOverviewSlotIndex(slot.index);
            if (page == null) continue;
            ItemStack stack = slot.getItem();
            // Skip empty slots: init fires before the server content packet, so slots are
            // temporarily empty. Skipping preserves correct snapshot titles until the packet
            // arrives and this method is called again with the real items.
            if (stack.isEmpty()) continue;
            StorageData.INSTANCE.updateInventory(page, stack.getHoverName().getString(), null, stack.copy());
        }
    }

    public static void onOverviewPacketReceived(AbstractContainerScreen<?> screen) {
        rememberOverview(screen);
    }

    /**
     * Collects all non-player-inventory slot items from the given container screen,
     * padded to a full row count (max 6 rows × 9 columns).
     * Returns an empty list if the player reference is unavailable.
     */
    public static List<ItemStack> collectContainerStacks(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return List.of();

        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != mc.player.getInventory()) {
                while (stacks.size() <= slot.index) stacks.add(ItemStack.EMPTY);
                stacks.set(slot.index, slot.getItem().copy());
            }
        }
        return normalizeToRows(stacks);
    }

    private static List<ItemStack> normalizeToRows(List<ItemStack> stacks) {
        int rows = Math.clamp((stacks.size() + 8) / 9, 1, 6);
        int target = rows * 9;
        while (stacks.size() < target) stacks.add(ItemStack.EMPTY);
        return stacks.size() > target ? stacks.subList(0, target) : stacks;
    }

    public static void save() {
        StorageSnapshotStorage storage = STORAGE_REF.getAndSet(null);
        if (storage != null) {
            storage.save(cachedProfileId, StorageData.INSTANCE);
        }
    }

    public static void clearCache() {
        StorageData.INSTANCE.clear();
        STORAGE_REF.set(null);
        cachedProfileId = "unknown";
        StorageOverlay.destroyActive();
    }

    private static String resolveProfileId() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getUUID().toString() : "unknown";
    }
}
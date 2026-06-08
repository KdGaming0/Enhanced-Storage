package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.azureaaron.hmapi.network.HypixelNetworking;
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
 * Feature lifecycle manager. Handles:
 * <ul>
 *   <li>Registering for Hypixel location updates via HM-API</li>
 *   <li>Loading/saving storage snapshots on server join/disconnect and client stop</li>
 *   <li>Detecting storage screens and creating the overlay delegate</li>
 *   <li>Capturing live inventory data from open containers</li>
 * </ul>
 */
public final class StorageLifecycle {

    private static final AtomicReference<StorageSnapshotStorage> STORAGE_REF = new AtomicReference<>();
    private static volatile String cachedProfileId = "unknown";
    private static volatile boolean onSkyBlock = false;

    private StorageLifecycle() {}

    public static void init() {
        // Register for HM-API location updates
        Object2IntOpenHashMap<net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket>> events =
                new Object2IntOpenHashMap<>();
        events.put(LocationUpdateS2CPacket.ID, 1);
        HypixelNetworking.registerToEvents(events);

        HypixelPacketEvents.LOCATION_UPDATE.register(StorageLifecycle::onLocationUpdate);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
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
        if (!EnhancedStorageConfig.enableStorageOverlay) return false;
        if (!Utils.isOnHypixel()) return false;
        return onSkyBlock;
    }

    public static @Nullable StorageOverlay createOverlay(AbstractContainerScreen<?> screen) {
        if (!isFeatureEnabled()) return null;

        String rawTitle = screen.getTitle().getString();
        Optional<StorageTitleParser.ParsedTitle> parsed = StorageTitleParser.parse(rawTitle);
        if (parsed.isEmpty()) return null;

        if (parsed.get().isOverview()) {
            rememberOverview(screen);
            return new StorageOverlay(screen, null);
        }

        StoragePage page = parsed.get().page();
        if (page == null) return null;

        rememberPage(screen, page, rawTitle);
        return new StorageOverlay(screen, page);
    }

    public static void rememberPage(AbstractContainerScreen<?> screen, StoragePage page, String rawTitle) {
        List<ItemStack> stacks = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != mc.player.getInventory()) {
                while (stacks.size() <= slot.index) stacks.add(ItemStack.EMPTY);
                stacks.set(slot.index, slot.getItem().copy());
            }
        }
        int rows = Math.clamp((stacks.size() + 8) / 9, 1, 6);
        int target = rows * 9;
        while (stacks.size() < target) stacks.add(ItemStack.EMPTY);
        if (stacks.size() > target) stacks = stacks.subList(0, target);

        VirtualInventory vinv = new VirtualInventory(stacks);
        StorageData.INSTANCE.updateInventory(page, rawTitle, vinv);
        StorageData.INSTANCE.markDirty();
    }

    public static void rememberOverview(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != mc.player.getInventory()) {
                StoragePage page = StoragePage.fromOverviewSlotIndex(slot.index);
                if (page == null) continue;
                ItemStack stack = slot.getItem();
                String title = stack.isEmpty() ? page.defaultName() : stack.getHoverName().getString();
                if (!StorageData.INSTANCE.hasInventory(page)) {
                    StorageData.INSTANCE.updateInventory(page, title, null);
                }
            }
        }
    }

    public static void onOverviewPacketReceived(AbstractContainerScreen<?> screen) {
        rememberOverview(screen);
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
    }

    private static String resolveProfileId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return mc.player.getUUID().toString();
        }
        return "unknown";
    }
}

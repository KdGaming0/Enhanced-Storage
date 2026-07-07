package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import com.github.kdgaming0.enhancedstorage.util.ProfileIdTracker;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.HypixelNetworking;
import net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.azureaaron.hmapi.utils.Utils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages feature lifecycle: server detection, snapshot persistence, and overlay creation.
 */
public final class StorageLifecycle {

    private static final StorageSnapshotStorage STORAGE = new StorageSnapshotStorage();

    /** Persistence key for the snapshot file — the SkyBlock profile UUID, or {@code null} when unknown. */
    private static volatile String currentKey = null;
    /** Whether {@link #currentKey} was confirmed by {@code /profileid} (vs. a tentative cache guess). */
    private static volatile boolean currentKeyConfirmed = false;
    private static volatile boolean onSkyBlock = false;

    private StorageLifecycle() {
    }

    public static void init() {
        Object2IntOpenHashMap<net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<HypixelS2CPacket>> events =
                new Object2IntOpenHashMap<>();
        events.put(LocationUpdateS2CPacket.ID, 1);
        HypixelNetworking.registerToEvents(events);
        HypixelPacketEvents.LOCATION_UPDATE.register(StorageLifecycle::onLocationUpdate);

        // The snapshot is keyed by SkyBlock profile UUID.
        Path cachePath = FabricLoader.getInstance().getConfigDir()
                .resolve(EnhancedStorage.MOD_ID).resolve("profile_cache.json");
        ProfileIdTracker.register(cachePath, StorageLifecycle::onProfileResolved, StorageLifecycle::onProfileEnded);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onSkyBlock = false);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveCurrent());
    }

    private static void onLocationUpdate(HypixelS2CPacket packet) {
        if (packet instanceof LocationUpdateS2CPacket loc) {
            onSkyBlock = loc.serverType()
                    .map(t -> t.equalsIgnoreCase("SKYBLOCK"))
                    .orElse(false);
        }
    }

    public static boolean isOnSkyBlock() {
        return onSkyBlock;
    }

    private static boolean isOnHypixelSkyBlock() {
        return Utils.isOnHypixel() && onSkyBlock;
    }

    public static @Nullable StorageOverlay createOverlay(AbstractContainerScreen<?> screen) {
        // Base gate only — the two overlay systems have independent config toggles checked below.
        if (!isOnHypixelSkyBlock()) {
            StorageOverlay.destroyActive();
            return null;
        }

        String rawTitle = screen.getTitle().getString();
        Optional<StorageTitleParser.ParsedTitle> parsed = StorageTitleParser.parse(rawTitle);
        if (parsed.isEmpty()) {
            StorageOverlay.destroyActive();
            return null;
        }

        StoragePage page = parsed.get().page();

        // Rift Storage is a separate two-page system with its own independent toggle.
        if (page != null && page.isRift()) {
            if (!EnhancedStorageConfig.enableRiftStorageOverlay) {
                StorageOverlay.destroyActive();
                return null;
            }
            rememberPage(screen, page, rawTitle);
            return StorageOverlay.createOrAttach(screen, page);
        }

        // Main storage hub (Ender Chest / Backpack).
        if (!EnhancedStorageConfig.enableStorageOverlay) {
            StorageOverlay.destroyActive();
            return null;
        }

        if (parsed.get().isOverview()) {
            rememberOverview(screen);
            return StorageOverlay.createOrAttach(screen, null);
        }

        if (page == null) {
            StorageOverlay.destroyActive();
            return null;
        }

        rememberPage(screen, page, rawTitle);
        return StorageOverlay.createOrAttach(screen, page);
    }

    public static void rememberPage(AbstractContainerScreen<?> screen, StoragePage page, String rawTitle) {
        List<ItemStack> stacks = collectContainerStacks(screen);
        if (stacks.isEmpty()) {
            EnhancedStorage.LOGGER.debug("Skipped capturing page {} — player/container slots unavailable", page);
            return;
        }
        // Serialize now, against the live connection's registry — see VirtualInventory.capture.
        StorageData.INSTANCE.updateInventory(page, rawTitle, VirtualInventory.capture(stacks, registryAccess()));
    }

    /** The current client registry set, or {@code null} when no level is loaded. */
    private static @Nullable HolderLookup.Provider registryAccess() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    public static void rememberOverview(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == mc.player.getInventory()) continue;
            StoragePage page = StoragePage.fromOverviewSlotIndex(slot.index);
            if (page == null) continue;
            ItemStack stack = slot.getItem();
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

    /**
     * Reacts to a profile UUID resolved by {@link ProfileIdTracker}.
     *
     * <p>Upholds the invariant that {@link StorageData#INSTANCE} is never saved under a key that
     * was a tentative guess later contradicted: on such a contradiction the in-memory state is
     * discarded (cleared and reloaded from disk), never persisted into the wrong file.
     *
     * @param profileId the resolved SkyBlock profile UUID
     * @param confirmed {@code true} if confirmed by {@code /profileid}; {@code false} if a
     *                  tentative value from the per-account cache
     */
    public static void onProfileResolved(UUID profileId, boolean confirmed) {
        String key = profileId.toString();

        if (key.equals(currentKey)) {
            if (confirmed && !currentKeyConfirmed) {
                currentKeyConfirmed = true;
                saveIfDirty();
            }
            return;
        }

        if (currentKey == null) {
            // First profile this session. Any pages already captured belong to this profile
            // (no other profile's file was loaded), so load() merges them without discarding.
            currentKey = key;
            currentKeyConfirmed = confirmed;
            STORAGE.load(key, StorageData.INSTANCE);
            return;
        }

        // Replacing a previously adopted key.
        if (currentKeyConfirmed) {
            // The prior key was confirmed — its data is legitimate; persist before switching.
            STORAGE.save(currentKey, StorageData.INSTANCE);
        }
        StorageData.INSTANCE.clear();
        currentKey = key;
        currentKeyConfirmed = confirmed;
        STORAGE.load(key, StorageData.INSTANCE);
        recaptureOpenScreen();
    }

    /**
     * Re-captures the currently open storage screen into {@link StorageData}, mirroring what the
     * content-packet handler does on open. Used after a mid-session profile switch so the overview's
     * locked/unlocked page state reflects the live screen immediately rather than the loaded snapshot.
     */
    private static void recaptureOpenScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        String rawTitle = screen.getTitle().getString();
        Optional<StorageTitleParser.ParsedTitle> parsed = StorageTitleParser.parse(rawTitle);
        if (parsed.isEmpty()) return;
        if (parsed.get().isOverview()) {
            rememberOverview(screen);
            return;
        }
        StoragePage page = parsed.get().page();
        if (page != null) {
            rememberPage(screen, page, rawTitle);
        }
    }

    /** Reacts to SkyBlock state ending (leave or disconnect): flush the current profile and reset. */
    public static void onProfileEnded() {
        saveCurrent();
        StorageData.INSTANCE.clear();
        currentKey = null;
        currentKeyConfirmed = false;
    }

    /** Saves the in-memory snapshot under the current key; no-op when no profile is known. */
    public static void saveCurrent() {
        if (currentKey == null) return;
        if (STORAGE.save(currentKey, StorageData.INSTANCE)) {
            StorageData.INSTANCE.clearDirty();
        }
    }

    /**
     * Persists the snapshot the moment a storage screen closes — but only when the profile UUID is
     * confirmed and there are unsaved changes. This bounds crash data loss to (at most) the page
     * still open, without rewriting the file when nothing changed. Saving only under a confirmed
     * key upholds the invariant that data is never written under a contradicted tentative guess.
     */
    public static void saveIfDirty() {
        if (currentKey == null || !currentKeyConfirmed) return;
        if (!StorageData.INSTANCE.isDirty()) return;
        if (STORAGE.save(currentKey, StorageData.INSTANCE)) {
            StorageData.INSTANCE.clearDirty();
        }
    }
}
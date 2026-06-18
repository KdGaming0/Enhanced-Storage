package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * JSON persistence layer for {@link StorageData}.
 * Saves/loads per-profile snapshot files with Base64-encoded NBT inventories.
 */
public final class StorageSnapshotStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FILE_FORMAT_VERSION = 3;
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path storageDir;

    public StorageSnapshotStorage() {
        this.storageDir = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(EnhancedStorage.MOD_ID)
                .resolve("storage");
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to create storage directory", e);
        }
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public void save(String profileId, StorageData data) {
        Path file = storageDir.resolve(sanitize(profileId) + ".json");
        Path temp = storageDir.resolve(sanitize(profileId) + ".tmp");

        Minecraft mc = Minecraft.getInstance();
        var lookup = mc.level != null ? mc.level.registryAccess() : null;

        JsonObject root = new JsonObject();
        root.addProperty("profileId", profileId);
        root.addProperty("version", FILE_FORMAT_VERSION);
        root.add("pages", serializePages(data.getInventories(), lookup));
        root.add("riftPages", serializePages(data.getRiftInventories(), lookup));

        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to save storage snapshot for {}", profileId, e);
        }
    }

    public void load(String profileId, StorageData data) {
        Path file = storageDir.resolve(sanitize(profileId) + ".json");
        if (!Files.exists(file)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var lookup = mc.level.registryAccess();

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;

            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            if (root.has("pages")) {
                deserializePages(root.getAsJsonArray("pages"), StorageType.MAIN, version, data, lookup);
            }
            if (root.has("riftPages")) {
                deserializePages(root.getAsJsonArray("riftPages"), StorageType.RIFT, version, data, lookup);
            }
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to load storage snapshot for {}", profileId, e);
            backupBrokenFile(file);
        }
    }

    /**
     * Encodes every populated entry of a page map (title + Base64 NBT inventory + icon).
     * Empty placeholder pages (no inventory and no icon) are skipped.
     */
    private static JsonArray serializePages(Map<StoragePage, StorageData.StorageInventory> inventories,
                                            HolderLookup.Provider lookup) {
        JsonArray pages = new JsonArray();
        for (Map.Entry<StoragePage, StorageData.StorageInventory> entry : inventories.entrySet()) {
            StoragePage page = entry.getKey();
            StorageData.StorageInventory inv = entry.getValue();
            if (inv.inventory() == null && (inv.icon() == null || inv.icon().isEmpty())) continue;

            JsonObject pageObj = new JsonObject();
            pageObj.addProperty("slotIndex", page.index());
            pageObj.addProperty("title", inv.title());

            if (inv.inventory() != null) {
                byte[] bytes = inv.inventory().serializeToBytes(lookup);
                if (bytes != null && bytes.length > 0) {
                    pageObj.addProperty("inventoryBase64",
                            java.util.Base64.getEncoder().encodeToString(bytes));
                }
            }

            if (inv.icon() != null && !inv.icon().isEmpty()) {
                String iconBase64 = ItemStackCodec.encode(inv.icon(), lookup);
                if (iconBase64 != null) {
                    pageObj.addProperty("iconBase64", iconBase64);
                }
            }

            pages.add(pageObj);
        }
        return pages;
    }

    /**
     * Decodes a page array into {@code data}, reconstructing each key with the given type.
     * The legacy {@code pageId} format only ever applied to MAIN pages (pre-v2 files).
     */
    private void deserializePages(JsonArray pages, StorageType type, int version,
                                  StorageData data, HolderLookup.Provider lookup) {
        for (var element : pages) {
            if (!element.isJsonObject()) continue;
            JsonObject pageObj = element.getAsJsonObject();

            StoragePage page;
            if (pageObj.has("slotIndex")) {
                page = new StoragePage(type, pageObj.get("slotIndex").getAsInt());
            } else if (type == StorageType.MAIN && pageObj.has("pageId")) {
                page = StoragePage.fromPageId(pageObj.get("pageId").getAsString());
                if (page == null) continue;
            } else {
                continue;
            }

            // Don't overwrite live data with stale file data
            if (data.hasInventory(page) && data.getInventory(page).inventory() != null) continue;

            String title = pageObj.has("title") ? pageObj.get("title").getAsString() : page.defaultName();
            String base64 = pageObj.has("inventoryBase64") ? pageObj.get("inventoryBase64").getAsString() : null;

            VirtualInventory vinv;
            if (base64 != null && !base64.isBlank()) {
                try {
                    byte[] bytes = java.util.Base64.getDecoder().decode(base64);
                    vinv = VirtualInventory.deserialize(bytes, lookup);
                } catch (Exception e) {
                    EnhancedStorage.LOGGER.warn("Failed to deserialize inventory for page {}", page, e);
                    vinv = null;
                }
            } else {
                vinv = null;
            }

            ItemStack icon = null;
            if (pageObj.has("iconBase64")) {
                icon = ItemStackCodec.decode(pageObj.get("iconBase64").getAsString(), lookup);
                if (icon.isEmpty()) icon = null;
            }

            data.updateInventory(page, title, vinv, icon);
        }
    }

    private void backupBrokenFile(Path file) {
        try {
            String timestamp = LocalDateTime.now().format(BACKUP_FORMAT);
            Path backup = file.resolveSibling(file.getFileName() + "." + timestamp + ".bak");
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to backup broken snapshot file", e);
        }
    }
}

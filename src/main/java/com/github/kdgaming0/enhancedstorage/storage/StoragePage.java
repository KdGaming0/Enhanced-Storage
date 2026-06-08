package com.github.kdgaming0.enhancedstorage.storage;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Typed, validated key representing a single storage page (Ender Chest or Backpack).
 * Encodes Hypixel's specific page indexing and navigation commands.
 */
public record StoragePage(int index) implements Comparable<StoragePage> {

    public static final int ENDER_CHEST_COUNT = 9;
    public static final int BACKPACK_COUNT = 18;
    public static final int COUNT = ENDER_CHEST_COUNT + BACKPACK_COUNT;

    public StoragePage {
        if (index < 0 || index >= COUNT) {
            throw new IllegalArgumentException("Invalid storage page index: " + index);
        }
    }

    public boolean isEnderChest() {
        return index < ENDER_CHEST_COUNT;
    }

    public boolean isBackpack() {
        return index >= ENDER_CHEST_COUNT;
    }

    /**
     * Returns the 1-based page number for display.
     */
    public int getPageNumber() {
        return isEnderChest() ? index + 1 : index - ENDER_CHEST_COUNT + 1;
    }

    public String defaultName() {
        return isEnderChest()
                ? "Ender Chest " + getPageNumber()
                : "Backpack " + getPageNumber();
    }

    /**
     * Sends the chat command to open this page.
     */
    public void navigateTo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        String command = isEnderChest()
                ? "ec " + getPageNumber()
                : "backpack " + getPageNumber();
        mc.getConnection().sendCommand(command);
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    public static StoragePage ofEnderChest(int page) {
        if (page < 1 || page > ENDER_CHEST_COUNT) {
            throw new IllegalArgumentException("Invalid Ender Chest page: " + page);
        }
        return new StoragePage(page - 1);
    }

    public static StoragePage ofBackpack(int page) {
        if (page < 1 || page > BACKPACK_COUNT) {
            throw new IllegalArgumentException("Invalid Backpack page: " + page);
        }
        return new StoragePage(ENDER_CHEST_COUNT + page - 1);
    }

    /**
     * Maps a slot index from the Hypixel Storage hub overview menu to a {@link StoragePage}.
     */
    public static @Nullable StoragePage fromOverviewSlotIndex(int slotIndex) {
        // Ender Chests occupy slots 9–17 in the hub menu
        if (slotIndex >= 9 && slotIndex <= 17) {
            return ofEnderChest(slotIndex - 8);
        }
        // Backpacks occupy slots 27–44 in the hub menu
        if (slotIndex >= 27 && slotIndex <= 44) {
            return ofBackpack(slotIndex - 26);
        }
        return null;
    }

    /**
     * Legacy ID parser for old persistence formats.
     */
    public static @Nullable StoragePage fromPageId(String id) {
        if (id == null || id.isBlank()) return null;
        String[] parts = id.split("_", 2);
        if (parts.length != 2) return null;
        try {
            int num = Integer.parseInt(parts[1]);
            return switch (parts[0].toLowerCase()) {
                case "ender", "storage" -> ofEnderChest(num);
                case "backpack" -> ofBackpack(num);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public int compareTo(@NotNull StoragePage o) {
        return Integer.compare(this.index, o.index);
    }
}

/*
 * Based on code from Firmament:
 * https://github.com/FirmamentMC/Firmament
 *
 * This file contains substantial portions adapted from Firmament's
 * storage overlay implementation.
 *
 * Original code licensed under the GNU General Public License v3.0.
 *
 * Modifications:
 * - Translated from Kotlin to Java
 * - Modified for Enhanced Storage
 */

package com.github.kdgaming0.enhancedstorage.storage;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Typed, validated key representing a single storage page. {@link StorageType#MAIN} pages are
 * the Hypixel Storage hub (Ender Chest / Backpack) navigated by chat commands; {@link
 * StorageType#RIFT} pages are the two Rift Storage pages navigated by in-container buttons.
 * The {@code type} is part of the page's identity so the two systems never collide.
 */
public record StoragePage(StorageType type, int index) implements Comparable<StoragePage> {

    public static final int ENDER_CHEST_COUNT = 9;
    public static final int BACKPACK_COUNT = 18;
    /** Number of {@link StorageType#MAIN} pages (Ender Chest + Backpack). */
    public static final int COUNT = ENDER_CHEST_COUNT + BACKPACK_COUNT;
    /** Number of {@link StorageType#RIFT} pages. */
    public static final int RIFT_COUNT = 2;

    // Slot indices of the interactive overview rows in the Hypixel Storage hub menu.
    public static final int OVERVIEW_EC_SLOT_FIRST = 9;
    public static final int OVERVIEW_EC_SLOT_LAST = 17;
    public static final int OVERVIEW_BP_ROW1_SLOT_FIRST = 27;
    public static final int OVERVIEW_BP_ROW1_SLOT_LAST = 35;
    public static final int OVERVIEW_BP_ROW2_SLOT_FIRST = 36;
    public static final int OVERVIEW_BP_ROW2_SLOT_LAST = 44;

    public StoragePage {
        int max = type == StorageType.RIFT ? RIFT_COUNT : COUNT;
        if (index < 0 || index >= max) {
            throw new IllegalArgumentException("Invalid " + type + " storage page index: " + index);
        }
    }

    /** Convenience constructor for {@link StorageType#MAIN} pages (Ender Chest / Backpack). */
    public StoragePage(int index) {
        this(StorageType.MAIN, index);
    }

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

    public static StoragePage ofRift(int page) {
        if (page < 1 || page > RIFT_COUNT) {
            throw new IllegalArgumentException("Invalid Rift Storage page: " + page);
        }
        return new StoragePage(StorageType.RIFT, page - 1);
    }

    /**
     * Maps a slot index from the Hypixel Storage hub overview menu to a {@link StoragePage}.
     */
    public static @Nullable StoragePage fromOverviewSlotIndex(int slotIndex) {
        if (slotIndex >= OVERVIEW_EC_SLOT_FIRST && slotIndex <= OVERVIEW_EC_SLOT_LAST) {
            return ofEnderChest(slotIndex - OVERVIEW_EC_SLOT_FIRST + 1);
        }
        if (slotIndex >= OVERVIEW_BP_ROW1_SLOT_FIRST && slotIndex <= OVERVIEW_BP_ROW2_SLOT_LAST) {
            return ofBackpack(slotIndex - OVERVIEW_BP_ROW1_SLOT_FIRST + 1);
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

    public boolean isRift() {
        return type == StorageType.RIFT;
    }

    public boolean isEnderChest() {
        return type == StorageType.MAIN && index < ENDER_CHEST_COUNT;
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    public boolean isBackpack() {
        return type == StorageType.MAIN && index >= ENDER_CHEST_COUNT;
    }

    /**
     * Returns the 1-based page number for display.
     */
    public int getPageNumber() {
        return isBackpack() ? index - ENDER_CHEST_COUNT + 1 : index + 1;
    }

    public String defaultName() {
        return switch (type) {
            case RIFT -> "Rift Storage " + (index + 1);
            case MAIN -> isEnderChest()
                    ? "Ender Chest " + getPageNumber()
                    : "Backpack " + getPageNumber();
        };
    }

    /**
     * Sends the Hypixel chat command that opens this storage page. Rift pages use the same
     * {@code /ec <n>} command as Ender Chest pages — Hypixel opens the rift's storage when the
     * player is in the Rift.
     */
    public void navigateTo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        String command = isBackpack()
                ? "backpack " + getPageNumber()
                : "ec " + getPageNumber();
        mc.getConnection().sendCommand(command);
    }

    @Override
    public int compareTo(@NotNull StoragePage o) {
        int byType = Integer.compare(this.type.ordinal(), o.type.ordinal());
        return byType != 0 ? byType : Integer.compare(this.index, o.index);
    }
}
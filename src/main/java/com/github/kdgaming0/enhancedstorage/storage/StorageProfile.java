package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Owns the "which SkyBlock profile are we on" state.
 * The profile id arrives in chat a few seconds after joining, so on join we
 * optimistically adopt the LAST known profile (persisted to disk). When the
 * real id arrives we either confirm it (no-op) or switch, which reloads the
 * per-profile caches and notifies listeners so the UI can rebuild.
 */
public final class StorageProfile {

    private static final StorageProfile INSTANCE = new StorageProfile();
    public static StorageProfile getInstance() { return INSTANCE; }

    /** Called when the active profile actually changes. */
    public interface Listener {
        void onProfileChanged(String newProfileId);
    }

    private volatile String currentProfileId;   // null = unknown
    private Runnable onChange;                  // set by the mod init

    private StorageProfile() {}

    public Optional<String> current() {
        return Optional.ofNullable(currentProfileId);
    }

    /** Registers the callback fired whenever the active profile changes. */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Called at world-join. Loads the last-seen profile from disk and adopts it
     * as the optimistic guess, WITHOUT firing the change callback (nothing is
     * open yet, and the caches will be loaded by the join handler anyway).
     */
    public void adoptLastKnownProfile() {
        this.currentProfileId = readPersistedProfile().orElse(null);
    }

    /**
     * Called when a "Profile ID: <uuid>" line is seen in chat. If it matches
     * what we already have, does nothing. If it differs (or we had none),
     * switches profiles: persists the new id and fires the change callback.
     */
    public void onProfileIdSeen(String profileId) {
        if (profileId == null || profileId.isBlank()) return;
        if (profileId.equals(currentProfileId)) return;   // 99% path: unchanged

        this.currentProfileId = profileId;
        persistProfile(profileId);

        if (onChange != null) {
            onChange.run();
        }
    }

    // ------------------------------------------------------------------
    // Persistence of the "last profile" pointer (a tiny text file)
    // ------------------------------------------------------------------

    private static Path profilePointerFile() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(EnhancedStorage.MOD_ID)
                .resolve("last_profile.txt");
    }

    private static Optional<String> readPersistedProfile() {
        Path file = profilePointerFile();
        if (!Files.exists(file)) return Optional.empty();
        try {
            String s = Files.readString(file).strip();
            return s.isBlank() ? Optional.empty() : Optional.of(s);
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to read last profile pointer", e);
            return Optional.empty();
        }
    }

    private static void persistProfile(String profileId) {
        try {
            Path file = profilePointerFile();
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, profileId);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            EnhancedStorage.LOGGER.error("Failed to persist last profile pointer", e);
        }
    }
}
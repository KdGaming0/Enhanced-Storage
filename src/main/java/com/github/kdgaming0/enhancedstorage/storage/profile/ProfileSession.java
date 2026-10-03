package com.github.kdgaming0.enhancedstorage.storage.profile;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.storage.StorageEvents;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.io.AtomicFiles;
import com.github.kdgaming0.enhancedstorage.storage.io.ProfileScopedStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.LOGGER;

/**
 * Owns the "which SkyBlock profile are we on" state, and saves/loads every
 * registered store for that profile.
 */
public final class ProfileSession {

    private static final ProfileSession INSTANCE = new ProfileSession();
    private static final long SAVE_DELAY_MS = 10_000;
    private final List<ProfileScopedStore> stores = new CopyOnWriteArrayList<>();
    // Pages captured while the profile was not confirmed yet
    private final Map<StorageKey, List<ItemStack>> staged = new LinkedHashMap<>();
    private volatile State state = State.UNKNOWN;
    private volatile String profileId;
    private long saveDueAt = Long.MAX_VALUE;
    private ProfileSession() {
    }

    public static ProfileSession getInstance() {
        return INSTANCE;
    }

    private static Path rootDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(EnhancedStorage.MOD_ID);
    }

    private static Path pointerFile() {
        return rootDir().resolve("last_profile.txt");
    }

    private static Optional<String> readPointer() {
        Path file = pointerFile();
        if (!Files.exists(file)) return Optional.empty();
        try {
            String text = Files.readString(file).strip();
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (IOException e) {
            LOGGER.error("Failed to read last profile pointer", e);
            return Optional.empty();
        }
    }

    private static void writePointer(String id) {
        try {
            AtomicFiles.writeString(pointerFile(), id);
        } catch (IOException e) {
            LOGGER.error("Failed to write last profile pointer", e);
        }
    }

    private Path profileDir() {
        String folder = profileId != null ? profileId : "default";
        return rootDir().resolve("profiles").resolve(folder);
    }

    /**
     * Call once at startup with every store that is saved per profile.
     */
    public void register(ProfileScopedStore... toRegister) {
        stores.addAll(List.of(toRegister));
    }

    public State state() {
        return state;
    }

    public boolean isConfirmed() {
        return state == State.CONFIRMED;
    }

    public Optional<String> current() {
        return Optional.ofNullable(profileId);
    }

    public List<ProfileScopedStore> stores() {
        return List.copyOf(stores);
    }

    public synchronized int stagedCount() {
        return staged.size();
    }

    /**
     * Saves within {@link #SAVE_DELAY_MS}, so a burst of changes becomes a single write.
     */
    public synchronized void requestSave() {
        saveDueAt = Math.min(saveDueAt, System.currentTimeMillis() + SAVE_DELAY_MS);
    }

    /**
     * Call once per client tick.
     */
    public synchronized void tick() {
        if (System.currentTimeMillis() >= saveDueAt) {
            saveAll();
        }
    }

    public synchronized void saveAll() {
        saveDueAt = Long.MAX_VALUE;
        if (state != State.CONFIRMED) {
            LOGGER.debug("Holding back saves; profile not confirmed yet");
            return;
        }
        Path dir = profileDir();
        for (ProfileScopedStore store : stores) {
            store.save(dir);
        }
    }

    private void reloadAll() {
        Path dir = profileDir();
        for (ProfileScopedStore store : stores) {
            store.load(dir);
        }
    }

    /**
     * Joined a world: adopt last session's profile as a guess and load its data.
     */
    public synchronized void onJoin() {
        staged.clear();
        profileId = readPointer().orElse(null);
        state = profileId != null ? State.GUESSED : State.UNKNOWN;
        reloadAll();
        LOGGER.info("Joined; profile is {} ({})", profileId, state);
    }

    public synchronized void onDisconnect() {
        saveAll();
    }

    /**
     * The game is closing: save and wait for the files to be written.
     */
    public synchronized void onStopping() {
        saveAll();
        AtomicFiles.awaitWrites();
    }

    /**
     * A "Profile ID: ..." line was seen in chat.
     */
    public synchronized void onProfileSeen(String id) {
        if (id == null || id.isBlank()) return;

        if (id.equals(profileId)) {
            // The guess was right. Confirm it and write what we held back.
            if (state != State.CONFIRMED) {
                state = State.CONFIRMED;
                staged.clear();   // those pages are already in the cache
                LOGGER.info("SkyBlock profile {} confirmed; flushing pending saves", id);
                saveAll();
            }
            return;
        }

        // A different profile. If we were already confirmed on another one this
        // session, flush that one first. A wrong, unconfirmed guess is NOT saved,
        // because its in-memory data may contain pages from the real profile.
        if (state == State.CONFIRMED) {
            saveAll();
        }

        LOGGER.info("SkyBlock profile changed from {} to {}", profileId, id);
        profileId = id;
        state = State.CONFIRMED;
        writePointer(id);

        reloadAll();
        replayStaged();
        StorageEvents.PROFILE_CHANGED.fire();
    }

    /**
     * The one place captured pages go. They always enter the cache (so the UI shows them
     * right away), and while the profile is unconfirmed they are also remembered here.
     */
    public synchronized void capture(StorageKey key, List<ItemStack> items) {
        List<ItemStack> snapshot = List.copyOf(items);   // shared: PageCache won't copy it again
        PageCache.getInstance().put(key, snapshot);
        if (state != State.CONFIRMED) {
            staged.put(key, snapshot);
        }
    }

    private void replayStaged() {
        if (staged.isEmpty()) return;
        PageCache cache = PageCache.getInstance();
        staged.forEach(cache::put);
        LOGGER.info("Replayed {} pages captured before the profile was known", staged.size());
        staged.clear();
    }

    public enum State {
        UNKNOWN,
        GUESSED,
        CONFIRMED
    }
}
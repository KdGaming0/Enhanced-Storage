package com.github.kdgaming0.enhancedstorage.storage.io;

import net.minecraft.nbt.CompoundTag;

import java.nio.file.Path;
import java.util.Optional;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.LOGGER;

/**
 * Base class for data that is saved per SkyBlock profile.
 */
public abstract class ProfileScopedStore {

    private static final String VERSION_KEY = "version";

    private final String fileName;
    private final int version;

    private volatile boolean dirty;
    private volatile boolean loaded;

    protected ProfileScopedStore(String fileName, int version) {
        this.fileName = fileName;
        this.version = version;
    }

    protected abstract void writeTo(CompoundTag root);
    protected abstract void readFrom(CompoundTag root, int fileVersion);
    protected abstract void clearMemory();

    // Subclasses call this whenever their data changes, so the next save actually writes.
    protected final void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public String fileName() {
        return fileName;
    }

    public void load(Path profileDir) {
        // A save still in flight could otherwise land after we read the file.
        AtomicFiles.awaitWrites();

        loaded = false;
        clearMemory();
        dirty = false;

        Optional<CompoundTag> root = AtomicFiles.read(profileDir.resolve(fileName));
        try {
            root.ifPresent(tag -> readFrom(tag, tag.getInt(VERSION_KEY).orElse(0)));
        } catch (RuntimeException e) {
            // Stay unloaded, so a save can't replace the file with partially read data.
            LOGGER.error("Failed to load {}; saving it is disabled until the next load", fileName, e);
            return;
        }

        loaded = true;
    }

    /** Writes the data to {@code profileDir} if it changed. The file is written in the background. */
    public void save(Path profileDir) {
        if (!dirty) return;
        if (!loaded) {
            LOGGER.debug("Not saving {}: never loaded, would risk overwriting real data", fileName);
            return;
        }

        // Clear the flag BEFORE writing, so a change made during the save marks it dirty again.
        dirty = false;

        CompoundTag root = new CompoundTag();
        try {
            writeTo(root);
        } catch (RuntimeException e) {
            dirty = true;
            LOGGER.error("Failed to save {}", fileName, e);
            return;
        }
        root.putInt(VERSION_KEY, version);

        AtomicFiles.writeAsync(profileDir.resolve(fileName), root, e -> {
            dirty = true;   // try again on the next save
            LOGGER.error("Failed to save {}", fileName, e);
        });
    }
}
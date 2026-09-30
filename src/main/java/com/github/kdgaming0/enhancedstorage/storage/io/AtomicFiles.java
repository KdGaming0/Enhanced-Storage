package com.github.kdgaming0.enhancedstorage.storage.io;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.LOGGER;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class AtomicFiles {

    private static final long AWAIT_TIMEOUT_SECONDS = 10;

    // single-threaded executor for writing files atomically.
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "EnhancedStorage-Writer");
        thread.setDaemon(true);
        return thread;
    });

    private AtomicFiles() {}

    @FunctionalInterface
    private interface TmpWriter {
        void write(Path tmp) throws IOException;
    }

    public static void write(Path file, CompoundTag tag) throws IOException {
        writeAtomically(file, tmp -> NbtIo.writeCompressed(tag, tmp));
    }

    public static void writeString(Path file, String text) throws IOException {
        writeAtomically(file, tmp -> Files.writeString(tmp, text));
    }

    /** Writes on the background thread. {@code tag} must not be modified afterwards. */
    public static void writeAsync(Path file, CompoundTag tag, Consumer<Exception> onFailure) {
        WRITER.execute(() -> {
            try {
                write(file, tag);
            } catch (IOException | RuntimeException e) {
                onFailure.accept(e);
            }
        });
    }

    /** Blocks until every pending background write has finished. */
    public static void awaitWrites() {
        try {
            WRITER.submit(() -> {}).get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("Pending storage writes did not finish in time", e);
        }
    }

    private static void writeAtomically(Path file, TmpWriter writer) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            writer.write(tmp);
            moveIntoPlace(tmp, file);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // Attempt an atomic move; if the filesystem doesn't support it, fall back to a non-atomic move.
    private static void moveIntoPlace(Path tmp, Path file) throws IOException {
        try {
            Files.move(tmp, file, REPLACE_EXISTING, ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, REPLACE_EXISTING);
        }
    }

    public static Optional<CompoundTag> read(Path file) {
        if (!Files.exists(file)) return Optional.empty();

        try {
            return Optional.of(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
        } catch (IOException e) {
            LOGGER.error("Could not read {}; moving it aside", file, e);
            quarantine(file);
            return Optional.empty();
        }
    }

    private static void quarantine(Path file) {
        Path corrupt = file.resolveSibling(file.getFileName() + ".corrupt");
        try {
            Files.move(file, corrupt, REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Could not quarantine {}", file, e);
        }
    }
}
package com.github.kdgaming0.enhancedstorage.storage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.kdgaming0.enhancedstorage.EnhancedStorage.LOGGER;

/**
 * Things that happen in the storage system that the UI may want to react to.
 * Listeners can be called from any thread, so a UI listener should hop onto
 * the main thread itself.
 */
public final class StorageEvents {

    public static final Signal INDEX_CHANGED = new Signal();
    public static final Signal PROFILE_CHANGED = new Signal();

    private StorageEvents() {
    }

    public static final class Signal {

        private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

        /**
         * Adds a listener to the signal.
         */
        public void subscribe(Runnable listener) {
            listeners.add(listener);
        }

        public void fire() {
            for (Runnable listener : listeners) {
                try {
                    listener.run();
                } catch (RuntimeException e) {
                    LOGGER.error("Storage event listener failed", e);
                }
            }
        }
    }
}
package com.github.kdgaming0.enhancedstorage.util;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.repo.io.AtomicFileWriter;
import com.github.kdgaming0.enhancedstorage.storage.StorageLifecycle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the current SkyBlock profile UUID by listening for Hypixel's automatic
 * {@code Profile ID: ...} system-chat messages, which are sent on every server change.
 *
 * <p>In this mod the profile UUID is a <em>persistence key</em> — it decides which storage
 * snapshot file is loaded and saved — so the tracker notifies {@link StorageLifecycle} of
 * resolution and reset events through callbacks rather than only exposing a getter.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Hypixel sends {@code Profile ID: ...} automatically on every SkyBlock server change,
 *       so the mod does not send {@code /profileid} itself.</li>
 *   <li>On entering SkyBlock the last cached profile UUID for the current account is reported
 *       <em>tentatively</em> ({@code confirmed = false}) so the previous snapshot loads
 *       instantly; the automatic message then verifies or corrects it in the background.</li>
 *   <li>State is reset on disconnect and when the player leaves SkyBlock; the reset fires the
 *       {@code onReset} callback so persistence can flush and clear.</li>
 * </ul>
 */
public final class ProfileIdTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            EnhancedStorage.MOD_ID + "/ProfileIdTracker");

    private static final Pattern PROFILE_ID_LINE =
            Pattern.compile("Profile ID: ([a-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type CACHE_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static Path cachePath;
    private static Map<String, String> profileCache = new HashMap<>();

    /** Notified with (profileId, confirmed); fired tentatively on enter, then on confirmation. */
    private static BiConsumer<UUID, Boolean> onProfileId = (id, confirmed) -> {};
    /** Notified when SkyBlock state ends (leave or disconnect) so persistence can flush + clear. */
    private static Runnable onReset = () -> {};

    private static volatile UUID profileId;
    private static boolean wasOnSkyblock = false;

    private ProfileIdTracker() {}

    /**
     * Registers the tick handler and disconnect reset; loads the persisted UUID cache.
     *
     * @param cacheFilePath where the per-account {@code account -> profile} cache is stored
     * @param onProfileId   called with (profileId, confirmed) on tentative load and confirmation
     * @param onReset       called when SkyBlock state ends (leave or disconnect)
     */
    public static void register(Path cacheFilePath, BiConsumer<UUID, Boolean> onProfileId, Runnable onReset) {
        cachePath = cacheFilePath;
        ProfileIdTracker.onProfileId = onProfileId;
        ProfileIdTracker.onReset = onReset;
        loadCache();
        ClientTickEvents.END_CLIENT_TICK.register(ProfileIdTracker::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    /**
     * Called by the incoming-chat mixin. Parses the profile UUID if present.
     *
     * <p>Hypixel sends {@code Profile ID: ...} automatically on every SkyBlock server change, so
     * no command needs to be sent by the mod. The callback is marshalled to the main thread
     * because it touches {@link com.github.kdgaming0.enhancedstorage.storage.StorageData} and the
     * overlay GUI.
     */
    public static void handleIncomingChat(String rawText) {
        if (rawText == null) return;
        String stripped = StringUtil.stripColorCodes(rawText).trim();
        if (stripped.isEmpty()) return;

        Matcher matcher = PROFILE_ID_LINE.matcher(stripped);
        if (matcher.find()) {
            try {
                UUID parsed = UUID.fromString(matcher.group(1));
                if (!parsed.equals(profileId)) {
                    profileId = parsed;
                    LOGGER.info("Detected SkyBlock profile UUID: {}", parsed);
                    Minecraft.getInstance().execute(() -> {
                        cacheProfileUuid(parsed);
                        onProfileId.accept(parsed, true);
                    });
                }
            } catch (IllegalArgumentException e) {
                LOGGER.error("Failed to parse profile UUID from: {}", stripped, e);
            }
        }
    }

    // ── Cache ──────────────────────────────────────────────────────────────────

    private static void loadCache() {
        if (cachePath == null) return;
        try {
            if (!Files.exists(cachePath)) {
                profileCache = new HashMap<>();
                return;
            }
            try (BufferedReader reader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8)) {
                Map<String, String> loaded = GSON.fromJson(reader, CACHE_TYPE);
                profileCache = (loaded != null) ? new HashMap<>(loaded) : new HashMap<>();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load profile UUID cache, starting fresh", e);
            profileCache = new HashMap<>();
        }
    }

    private static void cacheProfileUuid(UUID uuid) {
        if (cachePath == null) return;
        String account = accountUuid();
        String previous = profileCache.put(account, uuid.toString());
        if (uuid.toString().equals(previous)) return;
        try {
            AtomicFileWriter.writeJson(cachePath, profileCache, GSON);
        } catch (IOException e) {
            LOGGER.error("Failed to save profile UUID cache", e);
        }
    }

    private static UUID parseCached(String cached) {
        if (cached == null) return null;
        try {
            return UUID.fromString(cached);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Ignoring malformed cached profile UUID for {}: {}", accountUuid(), cached);
            return null;
        }
    }

    // ── Tick handler ───────────────────────────────────────────────────────────

    private static void onClientTick(Minecraft client) {
        boolean onSkyblock = StorageLifecycle.isOnSkyBlock();
        if (!onSkyblock) {
            if (wasOnSkyblock) {
                reset();
            }
            return;
        }

        if (!wasOnSkyblock) {
            // Defer entry handling until the player (and thus the world) is present: the
            // tentative load reads the world's registry, and the overlay needs a connection.
            if (client.player == null) return;

            // Just entered SkyBlock — report the cached profile tentatively, then wait for the
            // automatic Profile ID message to confirm or correct it.
            wasOnSkyblock = true;
            UUID cached = parseCached(profileCache.get(accountUuid()));
            if (cached != null) {
                onProfileId.accept(cached, false);
            }
        }
    }

    private static void reset() {
        boolean wasActive = wasOnSkyblock || profileId != null;
        profileId = null;
        wasOnSkyblock = false;
        if (wasActive) {
            onReset.run();
        }
    }

    private static String accountUuid() {
        var user = Minecraft.getInstance().getUser();
        return (user != null) ? user.getProfileId().toString() : "unknown";
    }
}

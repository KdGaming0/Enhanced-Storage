package com.github.kdgaming0.enhancedstorage.util;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
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
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the current SkyBlock profile UUID by probing the server with {@code /profileid}.
 *
 * <p>Adapted from SkyBlock-Enhancements. In this mod the profile UUID is a <em>persistence
 * key</em> — it decides which storage snapshot file is loaded and saved — so the tracker
 * notifies {@link StorageLifecycle} of resolution and reset events through callbacks rather
 * than only exposing a getter.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Probes are sent immediately when the player enters SkyBlock and then every
 *       {@value #RETRY_INTERVAL_TICKS} ticks until a response is received.</li>
 *   <li>On entering SkyBlock the last cached profile UUID for the current account is reported
 *       <em>tentatively</em> ({@code confirmed = false}) so the previous snapshot loads
 *       instantly; {@code /profileid} then verifies or corrects it in the background.</li>
 *   <li>The outgoing {@code /profileid} probe and its response (plus the suggestion spam) are
 *       hidden from chat, so the player never sees the mod probing.</li>
 *   <li>State is reset on disconnect and when the player leaves SkyBlock; the reset fires the
 *       {@code onReset} callback so persistence can flush and clear.</li>
 * </ul>
 */
public final class ProfileIdTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            EnhancedStorage.MOD_ID + "/ProfileIdTracker");

    private static final Pattern PROFILE_ID_LINE =
            Pattern.compile("Profile ID: ([a-z0-9\\-]+)", Pattern.CASE_INSENSITIVE);

    private static final String[] SUGGESTION_TEXTS = {
            "CLICK THIS TO SUGGEST IT IN CHAT [DASHES]",
            "CLICK THIS TO SUGGEST IT IN CHAT [NO DASHES]"
    };

    private static final int RETRY_INTERVAL_TICKS = 200; // 10 seconds
    private static final int SUGGESTION_HIDE_WINDOW_TICKS = 100; // 5 seconds

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type CACHE_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static Path cachePath;
    private static Map<String, String> profileCache = new HashMap<>();

    /** Notified with (profileId, confirmed); fired tentatively on enter, then on confirmation. */
    private static BiConsumer<UUID, Boolean> onProfileId = (id, confirmed) -> {};
    /** Notified when SkyBlock state ends (leave or disconnect) so persistence can flush + clear. */
    private static Runnable onReset = () -> {};

    // profileId / profileIdConfirmed are written on the network thread (chat parse) and read on
    // the main thread (tick); ticksSinceProbe is written on the main thread and read on the
    // network thread. The rest are main-thread only.
    private static volatile UUID profileId;
    private static volatile boolean profileIdConfirmed;
    private static boolean wasOnSkyblock = false;
    private static int retryTimer = 0;
    private static volatile int ticksSinceProbe = Integer.MAX_VALUE;
    private static boolean pendingSend = false;

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
     * Returns the current SkyBlock profile UUID, if known. The most recently confirmed UUID, or
     * — while on SkyBlock without a confirmation this session — the last cached UUID for the
     * current account, returned tentatively.
     */
    public static Optional<UUID> getProfileId() {
        if (profileId != null) {
            return Optional.of(profileId);
        }
        if (StorageLifecycle.isOnSkyBlock()) {
            UUID cached = parseCached(profileCache.get(accountUuid()));
            if (cached != null) return Optional.of(cached);
        }
        return Optional.empty();
    }

    /** {@code true} if the current profile UUID has been confirmed by {@code /profileid} this session. */
    public static boolean isProfileIdConfirmed() {
        return profileIdConfirmed;
    }

    /**
     * Called by the outgoing-chat mixin. Returns {@code true} if the message should be
     * suppressed because it is this tracker's internal {@code /profileid} probe.
     */
    public static boolean shouldSuppressOutgoingCommand(String message) {
        if (!pendingSend) return false;
        if (!"/profileid".equals(message)) return false;
        pendingSend = false;
        return true;
    }

    /**
     * Called by the incoming-chat mixin. Parses the profile UUID if present and returns
     * {@code true} if the message should be suppressed. The {@code Profile ID: ...} line and the
     * suggestion-spam follow-ups are both hidden, since the player never asked to see them.
     */
    public static boolean handleIncomingChat(String rawText) {
        if (rawText == null) return false;
        String stripped = TextUtil.stripColorCodes(rawText).trim();
        if (stripped.isEmpty()) return false;

        Matcher matcher = PROFILE_ID_LINE.matcher(stripped);
        if (matcher.matches()) {
            try {
                UUID parsed = UUID.fromString(matcher.group(1));
                if (!parsed.equals(profileId)) {
                    // The response mixin injects at HEAD, which runs on the network thread (before
                    // PacketUtils.ensureRunningOnSameThread). Set the dedup state synchronously, but
                    // marshal the file I/O and the persistence callback — which touch StorageData
                    // and the overlay GUI — onto the main thread.
                    profileId = parsed;
                    profileIdConfirmed = true;
                    LOGGER.info("Detected SkyBlock profile UUID: {}", parsed);
                    Minecraft.getInstance().execute(() -> {
                        cacheProfileUuid(parsed);
                        onProfileId.accept(parsed, true);
                    });
                }
            } catch (IllegalArgumentException e) {
                LOGGER.error("Failed to parse profile UUID from: {}", stripped, e);
            }
            return ticksSinceProbe <= SUGGESTION_HIDE_WINDOW_TICKS;
        }

        if (ticksSinceProbe <= SUGGESTION_HIDE_WINDOW_TICKS) {
            for (String text : SUGGESTION_TEXTS) {
                if (stripped.contains(text)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── Cache ──────────────────────────────────────────────────────────────────

    private static void loadCache() {
        if (cachePath == null) return;
        try {
            Files.createDirectories(cachePath.getParent());
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
            Path tmp = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(profileCache, writer);
            }
            Files.move(tmp, cachePath, StandardCopyOption.REPLACE_EXISTING);
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
            // tentative load reads the world's registry, and the probe needs a connection.
            if (client.player == null) return;

            // Just entered SkyBlock — report the cached profile tentatively, then probe.
            wasOnSkyblock = true;
            UUID cached = parseCached(profileCache.get(accountUuid()));
            if (cached != null) {
                onProfileId.accept(cached, false);
            }
            sendProfileIdCommand(client);
            retryTimer = 0;
            return;
        }

        ticksSinceProbe++;
        if (profileIdConfirmed) {
            return;
        }

        retryTimer++;
        if (retryTimer >= RETRY_INTERVAL_TICKS) {
            sendProfileIdCommand(client);
            retryTimer = 0;
        }
    }

    private static void sendProfileIdCommand(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return;
        pendingSend = true;
        ticksSinceProbe = 0;
        client.getConnection().sendCommand("profileid");
    }

    private static void reset() {
        boolean wasActive = wasOnSkyblock || profileId != null;
        profileId = null;
        profileIdConfirmed = false;
        wasOnSkyblock = false;
        retryTimer = 0;
        ticksSinceProbe = Integer.MAX_VALUE;
        pendingSend = false;
        if (wasActive) {
            onReset.run();
        }
    }

    private static String accountUuid() {
        var user = Minecraft.getInstance().getUser();
        return (user != null) ? user.getProfileId().toString() : "unknown";
    }
}

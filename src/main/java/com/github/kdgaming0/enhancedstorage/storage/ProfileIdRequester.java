package com.github.kdgaming0.enhancedstorage.storage;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.util.SkyblockState;
import com.github.kdgaming0.enhancedstorage.util.TextUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfileIdRequester {

    private static final Pattern PROFILE_ID_PATTERN = Pattern.compile("profile id:\\s*([0-9a-f-]{36})");
    private static final String SUGGEST_LINE = "click this to suggest it in chat";

    // Wait time before requesting the profile ID after entering SkyBlock.
    private static final int WAIT_TICKS = 200; // 10 seconds
    private static final int REQUEST_TIMEOUT_TICKS = 100; // 5 seconds
    private static final int SUPPRESS_TICKS_AFTER_SEND = 60; // 3 seconds
    private static final int SUPPRESS_TICKS_AFTER_REPLY = 20; // 1 second

    private static int ticksInSkyblock;
    private static int ticksWaitingForReply;
    private static int suppressProfileIdOutputTicks;
    private static boolean waitingForProfileId;
    private static boolean requestSent;

    private ProfileIdRequester() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ProfileIdRequester::tick);

        ClientReceiveMessageEvents.ALLOW_GAME.register(
                (message, overlay) -> overlay || onGameMessage(message));
    }

    public static void reset() {
        ticksInSkyblock = 0;
        ticksWaitingForReply = 0;
        waitingForProfileId = false;
        requestSent = false;
        suppressProfileIdOutputTicks = 0;
    }

    private static boolean onGameMessage(Component message) {
        String text = TextUtils.stripText(message);

        // Hide the optional suggestion line produced by our /profileid request.
        if (suppressProfileIdOutputTicks > 0
                && text.toLowerCase(Locale.ROOT).contains(SUGGEST_LINE)) {
            return false;
        }

        Matcher matcher = PROFILE_ID_PATTERN.matcher(text);
        if (!matcher.find()) {
            return true;
        }

        boolean wasOurRequest = waitingForProfileId;
        waitingForProfileId = false;

        // Keep filtering the optional /profileid lines briefly.
        if (wasOurRequest) {
            suppressProfileIdOutputTicks = SUPPRESS_TICKS_AFTER_REPLY;
        }

        handleProfileId(matcher.group(1));

        // Hides the profile ID if it was produced by our /profileid command.
        return !wasOurRequest;
    }

    private static void handleProfileId(String newProfileId) {
        StorageProfile profile = StorageProfile.getInstance();

        if (profile.current().filter(newProfileId::equals).isPresent()) {
            // If our optimistic guess (the profile id cached from the previous session) was right.
            // Confirm it and flush the captures we held back while the profile was still unknown.
            if (!profile.isConfirmed()) {
                profile.markConfirmed();

                EnhancedStorage.LOGGER.info(
                        "SkyBlock profile {} confirmed; flushing pending saves",
                        newProfileId
                );

                StorageProfile.saveCurrentProfileData();
            }
            return;
        }

        // If the cached profile is correct, save any new data captured this session before the confirmation.
        if (profile.isConfirmed()) {
            StorageProfile.saveCurrentProfileData();
        }

        EnhancedStorage.LOGGER.info(
                "SkyBlock profile changed from {} to {}",
                profile.current().orElse("unknown"),
                newProfileId
        );

        // Confirm the profile detected this session and switch to it. The change
        // listener loads its item cache, names, and ordering, then updates the overlay.
        profile.markConfirmed();
        profile.onProfileIdSeen(newProfileId);
    }

    private static void tick(Minecraft client) {
        if (suppressProfileIdOutputTicks > 0) {
            suppressProfileIdOutputTicks--;
        }

        // Stop treating profile ID messages as ours if Hypixel never replied.
        if (waitingForProfileId) {
            if (++ticksWaitingForReply >= REQUEST_TIMEOUT_TICKS) {
                EnhancedStorage.LOGGER.warn(
                        "No reply to /profileid after {}s; giving up until the next reset",
                        REQUEST_TIMEOUT_TICKS / 20);
                waitingForProfileId = false;
            }
            return;
        }

        // Once confirmed or requested, wait for the next connection reset.
        if (requestSent || StorageProfile.getInstance().isConfirmed()) return;

        if (client.player == null
                || client.getConnection() == null
                || !SkyblockState.isInSkyblock()) {
            ticksInSkyblock = 0;
            return;
        }

        if (++ticksInSkyblock < WAIT_TICKS) return;

        send(client);
    }

    private static void send(Minecraft client) {
        EnhancedStorage.LOGGER.info(
                "No SkyBlock profile id seen {}s after entering SkyBlock (single-profile account?); sending /profileid once",
                WAIT_TICKS / 20);

        waitingForProfileId = true;
        requestSent = true;
        ticksWaitingForReply = 0;
        suppressProfileIdOutputTicks = SUPPRESS_TICKS_AFTER_SEND;
        //noinspection DataFlowIssue
        client.getConnection().sendCommand("profileid");
    }
}

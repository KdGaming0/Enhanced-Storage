package com.github.kdgaming0.enhancedstorage.command;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlay;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayLayout;
import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayState;
import com.github.kdgaming0.enhancedstorage.storage.data.*;
import com.github.kdgaming0.enhancedstorage.storage.io.ProfileScopedStore;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileIdRequester;
import com.github.kdgaming0.enhancedstorage.storage.profile.ProfileSession;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class EnhancedStorageCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(commandRoot("enhancedstorage"));
            dispatcher.register(commandRoot("es"));
            dispatcher.register(searchCommand());
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> commandRoot(String name) {
        return literal(name)
                .then(searchCommand())
                .then(literal("config")
                        .executes(EnhancedStorageCommand::executeOpenConfig))
                .then(literal("edit")
                        .executes(ctx -> executeEdit(false))
                        .then(literal("rift")
                                .executes(ctx -> executeEdit(true))))
                .then(literal("layout")
                        .then(literal("export")
                                .executes(ctx -> executeExport(ctx, false))
                                .then(literal("rift")
                                        .executes(ctx -> executeExport(ctx, true))))
                        .then(literal("import")
                                .executes(ctx -> executeImport(false))
                                .then(literal("rift")
                                        .executes(ctx -> executeImport(true)))))
                .then(literal("test_storage")
                        .executes(EnhancedStorageCommand::executeTestStorage))
                .then(literal("debug")
                        .executes(EnhancedStorageCommand::executeDebug));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> searchCommand() {
        return literal("ecs")
                .executes(ctx -> openStorage(null))
                .then(argument("search_query", StringArgumentType.greedyString())
                        .executes(ctx -> openStorage(StringArgumentType.getString(ctx, "search_query"))));
    }

    private static int openStorage(@Nullable String searchQuery) {
        Minecraft client = Minecraft.getInstance();

        client.schedule(() -> {
            try {
                LocalPlayer player = client.player;
                if (player == null) return;
                if (searchQuery != null) StorageOverlayState.session().setSearchQuery(searchQuery);
                player.connection.sendCommand("storage");
            } catch (Exception e) {
                EnhancedStorage.LOGGER.error("Failed to open storage overlay", e);
            }
        });

        return 1;
    }

    private static int executeOpenConfig(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = Minecraft.getInstance();

        client.schedule(() -> {
            try {
                //? if >=26.2 {
                /*client.gui.setScreen(MidnightConfig.getScreen(client.gui.screen(), EnhancedStorage.MOD_ID));
                 *///?} else {
                client.setScreen(MidnightConfig.getScreen(client.screen, EnhancedStorage.MOD_ID));
                //?}
            } catch (Exception e) {
                EnhancedStorage.LOGGER.error("Failed to open config menu", e);
            }
        });

        ctx.getSource().sendFeedback(Component.literal("§a[EnhancedStorage] Opening configuration menu..."));
        return 1;
    }

    private static int executeTestStorage(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft.getInstance().schedule(() -> Minecraft.getInstance().setScreen(new StorageOverlay()));

        ctx.getSource().sendFeedback(Component.literal("§a[EnhancedStorage] Opening test storage overlay..."));
        return 1;
    }


    private static int executeEdit(boolean rift) {
        Minecraft.getInstance().schedule(() -> Minecraft.getInstance().setScreen(StorageOverlay.forEditMode(rift, false)));
        return 1;
    }

    private static int executeExport(CommandContext<FabricClientCommandSource> ctx, boolean rift) {
        PageLayout.Context context = PageLayout.Context.of(rift);
        LayoutSnapshot layout = StorageOverlayLayout.snapshot(context);
        String name = rift ? "Rift Storage" : "Storage";
        if (layout.pageCount() == 0) {
            ctx.getSource().sendError(Component.literal("[EnhancedStorage] No " + name
                    + " pages known yet. Open your " + name + " first."));
            return 0;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(LayoutTransfer.encode(context, layout));
        String importCommand = "/es layout import" + (rift ? " rift" : "");
        ctx.getSource().sendFeedback(Component.literal("§a[EnhancedStorage] Your " + name + " layout ("
                + layout.pageCount() + " pages) is now in your clipboard."));
        ctx.getSource().sendFeedback(Component.literal("§7Paste it into a chat, a message or a text file to keep it or "
                + "send it to a friend. To use it, copy the whole text again and type §f" + importCommand
                + "§7, or open Edit Layout > Share > Paste Layout."));
        return 1;
    }

    private static int executeImport(boolean rift) {
        Minecraft.getInstance().schedule(() -> Minecraft.getInstance().setScreen(StorageOverlay.forImport(rift)));
        return 1;
    }

    private static int executeDebug(CommandContext<FabricClientCommandSource> ctx) {
        ProfileSession session = ProfileSession.getInstance();

        List<String> lines = new ArrayList<>();
        lines.add("§e[EnhancedStorage] Debug §8(click to copy)");
        lines.add("§7Profile: §f" + session.current().orElse("none") + " §7(" + session.state() + ")");
        lines.add("§7Profile id request: §f" + ProfileIdRequester.debugPhase()
                + "§7, staged pages: §f" + session.stagedCount());

        for (ProfileScopedStore store : session.stores()) {
            lines.add("§7" + store.fileName()
                    + ": loaded=§f" + store.isLoaded()
                    + "§7, dirty=§f" + store.isDirty());
        }

        lines.add("§7Cached pages: §f" + PageCache.getInstance().all().size()
                + "§7, known pages: §f" + PageCache.getInstance().allKnown().size());
        lines.add("§7Custom names: §f" + PageNames.getInstance().all().size()
                + "§7, custom positions: §f" + PageOrder.getInstance().all().size());

        String report = lines.stream()
                .map(ChatFormatting::stripFormatting)
                .collect(Collectors.joining("\n"));

        Style clickToCopy = Style.EMPTY
                .withClickEvent(new ClickEvent.CopyToClipboard(report))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy the full report")));

        for (String line : lines) {
            ctx.getSource().sendFeedback(Component.literal(line).withStyle(clickToCopy));
        }
        return 1;
    }

}

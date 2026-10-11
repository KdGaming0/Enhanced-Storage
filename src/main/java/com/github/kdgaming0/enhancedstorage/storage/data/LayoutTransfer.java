package com.github.kdgaming0.enhancedstorage.storage.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Turns one context's layout into a line of text that can be pasted somewhere else, and back.
 * The text is {@link #PREFIX} followed by base64 of gzipped NBT, using the same entry format as the saved files.
 */
public final class LayoutTransfer {

    public static final String PREFIX = "ES1:";
    private static final int VERSION = 1;
    private static final int MAX_CODE_LENGTH = 100_000;
    private static final long MAX_NBT_BYTES = 2L * 1024 * 1024;
    private static final String VERSION_KEY = "version";
    private static final String CONTEXT_KEY = "context";
    private static final String LAYOUT_KEY = "layout";
    private static final String WIDTHS_KEY = "widths";
    private static final String NAMES_KEY = "names";

    private LayoutTransfer() {
    }

    public static String encode(PageLayout.Context context, LayoutSnapshot layout) {
        CompoundTag root = new CompoundTag();
        root.putInt(VERSION_KEY, VERSION);
        root.putString(CONTEXT_KEY, context.id());
        root.put(LAYOUT_KEY, PageLayout.writeEntries(layout.entries()));
        CompoundTag widths = new CompoundTag();
        layout.widths().forEach((key, width) -> widths.putInt(key.id(), width));
        root.put(WIDTHS_KEY, widths);
        CompoundTag names = new CompoundTag();
        layout.names().forEach((key, name) -> names.putString(key.id(), name));
        root.put(NAMES_KEY, names);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(root, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Writing to memory failed", e);
        }
        return PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    /**
     * Reads a code. Pages that don't belong to the code's context are dropped, and widths and spaces are clamped to
     * what the editor allows, so a hand-edited code can't produce a layout the editor couldn't.
     *
     * @throws InvalidCodeException with a message that can be shown to the player
     */
    public static Shared decode(String text) throws InvalidCodeException {
        // Chat apps can wrap long lines, so whitespace anywhere is ignored.
        String code = text.replaceAll("\\s+", "");
        if (!code.startsWith(PREFIX)) {
            throw new InvalidCodeException(code.matches("ES\\d+:.*")
                    ? "This layout is from a newer version of Enhanced Storage."
                    : "That isn't an Enhanced Storage layout.");
        }
        if (code.length() > MAX_CODE_LENGTH) throw new InvalidCodeException("That text is too long to be a layout.");

        CompoundTag root;
        try {
            byte[] bytes = Base64.getDecoder().decode(code.substring(PREFIX.length()));
            root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(MAX_NBT_BYTES));
        } catch (Exception e) {
            throw new InvalidCodeException("That layout is incomplete or damaged. Copy all of it again.");
        }

        if (root.getIntOr(VERSION_KEY, 0) > VERSION) {
            throw new InvalidCodeException("This layout is from a newer version of Enhanced Storage.");
        }
        PageLayout.Context context = root.getString(CONTEXT_KEY).flatMap(PageLayout.Context::fromId)
                .orElseThrow(() -> new InvalidCodeException("That layout is incomplete or damaged. Copy all of it again."));

        List<LayoutEntry> entries = new ArrayList<>();
        for (LayoutEntry entry : PageLayout.readEntries(root.getListOrEmpty(LAYOUT_KEY))) {
            switch (entry) {
                case LayoutEntry.Page(StorageKey key) -> {
                    if (context.holds(key)) entries.add(entry);
                }
                case LayoutEntry.Stack(List<StorageKey> keys) -> {
                    List<StorageKey> kept = keys.stream().filter(context::holds).toList();
                    if (!kept.isEmpty()) entries.add(LayoutOps.stackOf(kept));
                }
                case LayoutEntry.Spacer spacer -> entries.add(spacer.fill() ? spacer
                        : spacer.withSlots(Math.clamp(spacer.slots(), PageWidths.MIN_WIDTH, PageWidths.MAX_WIDTH)));
                case LayoutEntry.Section section -> entries.add(section.withName(capName(section.name())));
                case LayoutEntry.SectionEnd end -> entries.add(end);
            }
        }

        Map<StorageKey, Integer> widths = new HashMap<>();
        CompoundTag widthTag = root.getCompoundOrEmpty(WIDTHS_KEY);
        for (String id : widthTag.keySet()) {
            StorageKey.fromId(id).filter(context::holds).ifPresent(key -> widthTag.getInt(id).ifPresent(width ->
                    widths.put(key, Math.clamp(width, PageWidths.MIN_WIDTH, PageWidths.MAX_WIDTH))));
        }

        Map<StorageKey, String> names = new HashMap<>();
        CompoundTag nameTag = root.getCompoundOrEmpty(NAMES_KEY);
        for (String id : nameTag.keySet()) {
            StorageKey.fromId(id).filter(context::holds).ifPresent(key -> nameTag.getString(id).ifPresent(name -> {
                if (!name.isBlank()) names.put(key, capName(name));
            }));
        }

        return new Shared(context, new LayoutSnapshot(entries, widths, names));
    }

    private static String capName(String name) {
        if (name.length() <= PageNames.MAX_NAME_LENGTH) return name;
        int end = PageNames.MAX_NAME_LENGTH;
        // Don't cut an emoji (surrogate pair) in half.
        if (Character.isHighSurrogate(name.charAt(end - 1))) end--;
        return name.substring(0, end);
    }

    public record Shared(PageLayout.Context context, LayoutSnapshot layout) {
    }

    public static final class InvalidCodeException extends Exception {
        public InvalidCodeException(String message) {
            super(message);
        }
    }
}

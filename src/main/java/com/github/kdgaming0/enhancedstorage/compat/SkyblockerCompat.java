package com.github.kdgaming0.enhancedstorage.compat;

import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import com.github.kdgaming0.enhancedstorage.config.EnhancedStorageConfig;
import com.github.kdgaming0.enhancedstorage.storage.data.PageCache;
import com.github.kdgaming0.enhancedstorage.storage.data.StorageKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Page values for the $ button next to page titles, priced by Skyblocker.
 * <p>
 * Skyblocker's own Chest Value only runs on the vanilla chest screen, so it never appears on ours, and it can only
 * value the chest that is open. This adds up a page's saved items the same way Skyblocker values a plain chest
 * ({@code ChestValue.getChestValue}, type OTHER): each item with a SkyBlock id counts its networth for its stack
 * size, and the total is "incomplete" when any of them has no price. The items are what the page held when it was
 * last seen; the prices are Skyblocker's current ones.
 * <p>
 * Skyblocker is a compile-only dependency, so every reference to its classes stays behind {@link #LOADED}.
 * {@code NetworthCalculator} returns a type from a library nested in Skyblocker's jar, so that call is reflective. Any
 * failure logs once and hides the button for the rest of the session.
 */
public final class SkyblockerCompat {

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("skyblocker");
    // Skyblocker refreshes its price data every few minutes; re-add a shown page now and then to follow it.
    private static final long REPRICE_MS = 60_000;
    // An incomplete total may only mean Skyblocker hadn't loaded its prices yet (e.g. right after joining).
    private static final long REPRICE_INCOMPLETE_MS = 5_000;
    // Used only if Skyblocker's own number formats can't be read.
    private static final NumberFormat SHORT_FALLBACK = Util.make(
            NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT), f -> f.setMaximumFractionDigits(1));

    private static boolean failed;
    private static @Nullable Method getItemNetworth;
    private static @Nullable Method networthPrice;
    private static final Map<StorageKey, Priced> values = new HashMap<>();

    /**
     * Worth of one page, in coins. Incomplete when some item had no price.
     */
    public record PageValue(long coins, boolean incomplete) {
    }

    private record Priced(PageCache.CachedPage page, PageValue value, long at) {
    }

    private SkyblockerCompat() {
    }

    /**
     * Whether pages get a $ button: Skyblocker is installed, the setting is on and nothing has failed.
     */
    public static boolean pageValueEnabled() {
        return LOADED && !failed && EnhancedStorageConfig.showPageValueButton;
    }

    /**
     * Value of the page's saved items, or empty when the page isn't saved or pricing failed. Re-added only when the
     * page's contents change or the last total is a minute old (a few seconds while it's incomplete), so it's cheap
     * to call every frame.
     */
    public static Optional<PageValue> pageValue(StorageKey key) {
        if (!pageValueEnabled()) return Optional.empty();
        Optional<PageCache.CachedPage> page = PageCache.getInstance().get(key);
        if (page.isEmpty()) return Optional.empty();

        long now = Util.getMillis();
        Priced priced = values.get(key);
        long maxAge = priced != null && priced.value().incomplete() ? REPRICE_INCOMPLETE_MS : REPRICE_MS;
        if (priced == null || priced.page() != page.get() || now - priced.at() > maxAge) {
            PageValue value = price(page.get().items());
            if (value == null) return Optional.empty();
            priced = new Priced(page.get(), value, now);
            values.put(key, priced);
        }
        return Optional.of(priced.value());
    }

    /**
     * Short form for the page title row, like {@code 12.3M}, in Skyblocker's Chest Value colours.
     */
    public static Component shortText(PageValue value) {
        String text;
        try {
            text = de.hysky.skyblocker.utils.Formatters.SHORT_FLOAT_NUMBERS.format(value.coins());
        } catch (LinkageError | RuntimeException e) {
            text = SHORT_FALLBACK.format(value.coins());
        }
        return Component.literal(text).withStyle(color(value));
    }

    /**
     * Full form for the tooltip, like Skyblocker shows it: {@code 12,345,678 Coins}.
     */
    public static Component fullText(PageValue value) {
        String text;
        try {
            text = de.hysky.skyblocker.utils.Formatters.INTEGER_NUMBERS.format(value.coins());
        } catch (LinkageError | RuntimeException e) {
            text = NumberFormat.getIntegerInstance(Locale.US).format(value.coins());
        }
        return Component.literal(text + " Coins").withStyle(color(value));
    }

    private static ChatFormatting color(PageValue value) {
        try {
            var config = de.hysky.skyblocker.config.SkyblockerConfigManager.get().uiAndVisuals.chestValue;
            ChatFormatting color = value.incomplete() ? config.incompleteColor : config.color;
            return color != null ? color : ChatFormatting.GOLD;
        } catch (LinkageError | RuntimeException e) {
            return ChatFormatting.GOLD;
        }
    }

    private static @Nullable PageValue price(List<ItemStack> items) {
        if (!resolve()) return null;
        try {
            double total = 0;
            boolean incomplete = false;
            for (ItemStack stack : items) {
                if (stack == null || stack.isEmpty()) continue;
                String apiId = de.hysky.skyblocker.utils.ItemUtils.getSkyblockApiId(stack);
                if (apiId == null || apiId.isEmpty()) continue;
                if (de.hysky.skyblocker.utils.ItemUtils.getItemPrice(apiId).isEmpty()) incomplete = true;
                Object result = getItemNetworth.invoke(null, stack, stack.getCount());
                total += ((Number) networthPrice.invoke(result)).doubleValue();
            }
            return new PageValue((long) total, incomplete);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            fail("pricing a page failed", e);
            return null;
        }
    }

    private static boolean resolve() {
        if (failed) return false;
        if (getItemNetworth != null) return true;
        try {
            Class<?> calculator = Class.forName("de.hysky.skyblocker.utils.networth.NetworthCalculator");
            Method networth = calculator.getMethod("getItemNetworth", ItemStack.class, int.class);
            networthPrice = networth.getReturnType().getMethod("price");
            getItemNetworth = networth;
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            fail("Skyblocker's networth calculator wasn't found", e);
            return false;
        }
    }

    private static void fail(String what, Throwable t) {
        if (failed) return;
        failed = true;
        values.clear();
        EnhancedStorage.LOGGER.warn("Skyblocker page value: {}; hiding the page value button. "
                + "This usually means a Skyblocker update changed its internals.", what, t);
    }
}

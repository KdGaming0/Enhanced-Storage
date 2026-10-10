package com.github.kdgaming0.enhancedstorage.compat;

import cc.cassian.rrv.common.overlay.OverlayManager;
import cc.cassian.rrv.common.overlay.itemlist.view.ItemViewOverlay;
import cc.cassian.rrv.common.overlay.itemlist.view.SearchBar;
import com.github.kdgaming0.enhancedstorage.EnhancedStorage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public final class RRVCompat {
    private static final MethodType HIGHLIGHT_TYPE = MethodType.methodType(void.class, ItemViewOverlay.class, Screen.class, GuiGraphicsExtractor.class, int.class, int.class, float.class);
    private static final MethodHandle RENDER_ITEM_HIGHLIGHTING = bindRenderItemHighlighting();
    private static final boolean RENDER_ALL_BACKGROUND_AVAILABLE = hasRenderAllBackground();
    private static boolean renderingEarly = false;
    private static boolean renderingHighlightLate = false;

    private RRVCompat() {
    }

    public static boolean isRenderingEarly() {
        return renderingEarly;
    }

    public static boolean isRenderingHighlightLate() {
        return renderingHighlightLate;
    }

    public static boolean isSearchBarFocused(Screen screen) {
        return screen.getFocused() instanceof SearchBar;
    }

    /**
     * Gives RRV's search bar focus again, e.g. after a rebuild took it away while the player was typing.
     */
    public static void focusSearchBar(Screen screen) {
        SearchBar searchBar = ItemViewOverlay.INSTANCE.getSearchbar();
        if (searchBar == null || !searchBar.isVisible()) return;
        screen.setFocused(searchBar);
        searchBar.setFocused(true);
    }

    private static boolean hasRenderAllBackground() {
        try {
            OverlayManager.class.getMethod("renderAllBackground", GuiGraphicsExtractor.class, int.class, int.class, float.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static boolean canDeferHighlight() {
        return RENDER_ITEM_HIGHLIGHTING != null;
    }

    // Render the RRV overlay below the Storage Overlay so the item list nav buttons don't interfere with the overlay when it is open.
    public static void renderOverlayBelow(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderingEarly = true;
        try {
            // RRV normally draws its panel backgrounds in Fabric's after-background event, which comes after our early
            // item list; the recipe book theme's opaque panel would then hide the items, so the backgrounds go first here.
            if (RENDER_ALL_BACKGROUND_AVAILABLE) {
                OverlayManager.INSTANCE.renderAllBackground(guiGraphics, mouseX, mouseY, partialTicks);
            }
            OverlayManager.INSTANCE.renderAll(guiGraphics, mouseX, mouseY, partialTicks);
        } finally {
            renderingEarly = false;
        }
    }

    // Render the Filter Highlight Above again so they are visible
    public static void renderHighlightAbove(AbstractContainerScreen<?> screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        MethodHandle handle = RENDER_ITEM_HIGHLIGHTING;
        if (handle == null) return;

        renderingHighlightLate = true;
        try {
            handle.invokeExact(ItemViewOverlay.INSTANCE, (Screen) screen, guiGraphics, mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            throw new RuntimeException("Enhanced Storage: RRV renderItemHighlighting threw", t);
        } finally {
            renderingHighlightLate = false;
        }
    }

    private static MethodHandle bindRenderItemHighlighting() {
        for (Method method : ItemViewOverlay.class.getDeclaredMethods()) {
            if (!method.getName().equals("renderItemHighlighting")) continue;

            Class<?>[] params = method.getParameterTypes();
            // The screen parameter only has to accept what we pass; everything after it we read positionally.
            if (params.length != 5
                    || !params[0].isAssignableFrom(AbstractContainerScreen.class)
                    || params[1] != GuiGraphicsExtractor.class
                    || params[2] != int.class
                    || params[3] != int.class
                    || params[4] != float.class) {
                continue;
            }

            try {
                method.setAccessible(true);
                // asType inserts the checked cast that reconciles our Screen callsite with whatever RRV declares.
                return MethodHandles.lookup().unreflect(method).asType(HIGHLIGHT_TYPE);
            } catch (ReflectiveOperationException | RuntimeException e) {
                EnhancedStorage.LOGGER.warn("Could not bind RRV renderItemHighlighting; filter highlights will render under the storage overlay.", e);
                return null;
            }
        }

        EnhancedStorage.LOGGER.warn("RRV's renderItemHighlighting is missing or has an unrecognised signature; filter highlights will render under the storage overlay.");
        return null;
    }
}

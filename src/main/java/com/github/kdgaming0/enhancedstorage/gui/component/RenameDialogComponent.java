package com.github.kdgaming0.enhancedstorage.gui.component;

import com.daqem.uilib.api.component.IComponent;
import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.component.AbstractComponent;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.daqem.uilib.gui.widget.ButtonWidget;
import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.github.kdgaming0.enhancedstorage.storage.StorageKey;
import com.github.kdgaming0.enhancedstorage.storage.StorageNames;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * A modal dialog for renaming a storage page. Rendered on top of the storage
 * overlay: a dimming scrim covers the whole screen, with a centered panel
 * holding a title, a single-line {@link EditBoxWidget}, and Save / Cancel
 * buttons (plus Reset when a custom name already exists).
 *
 * <p>This component is purely visual + input; the owning screen supplies the
 * callbacks that actually persist, clear, or discard the name.</p>
 */
public class RenameDialogComponent extends AbstractComponent {

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 88;

    // Scrim + panel colours (ARGB). The panel is a simple filled rect with a
    // lighter border so the dialog reads clearly over any background type.
    private static final int SCRIM_COLOR   = 0xC0000000;
    private static final int PANEL_FILL    = 0xF0202020;
    private static final int PANEL_BORDER  = 0xFF000000;
    private static final int PANEL_HILIGHT = 0xFF555555;

    private final StorageKey key;
    private final EditBoxWidget nameBox;

    private final int panelX;
    private final int panelY;

    /**
     * @param screenWidth  full screen width  (scrim spans this)
     * @param screenHeight full screen height (scrim spans this)
     * @param font         client font
     * @param key          the page being renamed
     * @param onSave       accepts the entered name (already the raw box value)
     * @param onCancel     discards without changes
     * @param onReset      clears the custom name; null hides the Reset button
     */
    public RenameDialogComponent(int screenWidth, int screenHeight, Font font,
                                 StorageKey key,
                                 java.util.function.Consumer<String> onSave,
                                 Runnable onCancel,
                                 Runnable onReset) {
        super(0, 0, screenWidth, screenHeight);
        this.key = key;

        this.panelX = (screenWidth - PANEL_WIDTH) / 2;
        this.panelY = (screenHeight - PANEL_HEIGHT) / 2;

        boolean hasCustom = StorageNames.getInstance().has(key);

        // Title
        String defaultName = key.displayName();
        TextComponent title = new TextComponent(
                panelX + 8, panelY + 8,
                Component.literal("Rename \"" + defaultName + "\""),
                0xFFFFFFFF);
        title.setDrawShadow(true);
        this.addComponent(title);

        // Edit box, prefilled with the current custom name (if any).
        int boxX = panelX + 8;
        int boxY = panelY + 24;
        int boxWidth = PANEL_WIDTH - 16;
        this.nameBox = new EditBoxWidget(font, boxX, boxY, boxWidth, 16,
                Component.literal("Page name"));
        this.nameBox.setValue(StorageNames.getInstance().get(key).orElse(""));
        this.addWidget(this.nameBox);

        // Buttons. Reset only appears when a custom name already exists; when it
        // does, all three share a row, otherwise Save/Cancel split the width.
        int btnY = panelY + PANEL_HEIGHT - 26;
        int gap = 6;
        int innerWidth = PANEL_WIDTH - 16;

        if (hasCustom && onReset != null) {
            int btnWidth = (innerWidth - gap * 2) / 3;
            ButtonWidget saveBtn = new ButtonWidget(panelX + 8, btnY, btnWidth, 20,
                    Component.literal("Save"), b -> onSave.accept(nameBox.getValue()));
            ButtonWidget resetBtn = new ButtonWidget(panelX + 8 + btnWidth + gap, btnY, btnWidth, 20,
                    Component.literal("Reset"), b -> onReset.run());
            ButtonWidget cancelBtn = new ButtonWidget(panelX + 8 + (btnWidth + gap) * 2, btnY, btnWidth, 20,
                    Component.literal("Cancel"), b -> onCancel.run());
            this.addWidget(saveBtn);
            this.addWidget(resetBtn);
            this.addWidget(cancelBtn);
        } else {
            int btnWidth = (innerWidth - gap) / 2;
            ButtonWidget saveBtn = new ButtonWidget(panelX + 8, btnY, btnWidth, 20,
                    Component.literal("Save"), b -> onSave.accept(nameBox.getValue()));
            ButtonWidget cancelBtn = new ButtonWidget(panelX + 8 + btnWidth + gap, btnY, btnWidth, 20,
                    Component.literal("Cancel"), b -> onCancel.run());
            this.addWidget(saveBtn);
            this.addWidget(cancelBtn);
        }
    }

    public StorageKey getKey() { return key; }

    public EditBoxWidget getNameBox() { return nameBox; }

    /** True if the point is inside the dialog panel (not just the scrim). */
    public boolean isOverPanel(double x, double y) {
        return x >= panelX && x < panelX + PANEL_WIDTH
                && y >= panelY && y < panelY + PANEL_HEIGHT;
    }

    // Render order: scrim + panel first (in extractRenderState), then child
    // widgets (edit box, buttons), then any child components (the title).
    @Override
    public void extractRenderStateBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                       float partialTick, int parentWidth, int parentHeight) {
        this.extractRenderState(guiGraphics, mouseX, mouseY, partialTick, parentWidth, parentHeight);

        for (IWidget widget : getWidgets()) {
            widget.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }

        for (IComponent component : getComponents()) {
            component.extractRenderStateBase(guiGraphics, mouseX, mouseY, partialTick, getWidth(), getHeight());
        }
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                   float partialTick, int parentWidth, int parentHeight) {
        // Full-screen dimming scrim.
        guiGraphics.fill(0, 0, getWidth(), getHeight(), SCRIM_COLOR);

        // Panel: border, subtle top/left highlight, then fill.
        guiGraphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, PANEL_BORDER);
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_FILL);
        // 1px inner highlight along the top and left edges.
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, PANEL_HILIGHT);
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, PANEL_HILIGHT);
    }
}
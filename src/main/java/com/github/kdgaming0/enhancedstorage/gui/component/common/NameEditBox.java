package com.github.kdgaming0.enhancedstorage.gui.component.common;

import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.github.kdgaming0.enhancedstorage.gui.OverlayStyle;
import com.github.kdgaming0.enhancedstorage.storage.data.PageNames;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * The borderless box page titles and section names are renamed in. While empty, it shows the default name as a hint.
 */
public final class NameEditBox {

    private NameEditBox() {
    }

    public static EditBoxWidget create(Font font, int x, int y, int width, String narration, String defaultName,
                                       String initial) {
        EditBoxWidget box = new EditBoxWidget(font, x, y, width, font.lineHeight, Component.literal(narration));
        box.setBordered(false);
        box.setMaxLength(PageNames.MAX_NAME_LENGTH);
        box.setTextColor(OverlayStyle.titleColor());
        box.setResponder(text -> box.setSuggestion(text.isEmpty() ? defaultName : null));
        box.setValue(initial);
        box.setSuggestion(box.getValue().isEmpty() ? defaultName : null);
        return box;
    }
}

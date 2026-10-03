package com.github.kdgaming0.enhancedstorage.gui.component.page;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;

/**
 * One cell of the page grid: a page card or a spacer.
 */
public abstract class GridCellComponent extends AbstractComponent {

    protected GridCellComponent(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public abstract LayoutEntry entry();

    public Rect bounds() {
        return new Rect(getTotalX(), getTotalY(), getWidth(), getHeight());
    }
}
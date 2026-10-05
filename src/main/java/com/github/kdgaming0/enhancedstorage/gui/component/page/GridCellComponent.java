package com.github.kdgaming0.enhancedstorage.gui.component.page;

import com.daqem.uilib.gui.component.AbstractComponent;
import com.github.kdgaming0.enhancedstorage.gui.layout.Rect;
import com.github.kdgaming0.enhancedstorage.storage.data.LayoutEntry;

/**
 * One cell of the page grid: a page card or a spacer. Stacked page cards are consecutive cells with the same stack tag.
 */
public abstract class GridCellComponent extends AbstractComponent {

    public static final int NO_STACK = -1;

    private int stackTag = NO_STACK;

    protected GridCellComponent(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public int stackTag() {
        return stackTag;
    }

    public void setStackTag(int stackTag) {
        this.stackTag = stackTag;
    }

    public abstract LayoutEntry entry();

    public Rect bounds() {
        return new Rect(getTotalX(), getTotalY(), getWidth(), getHeight());
    }
}
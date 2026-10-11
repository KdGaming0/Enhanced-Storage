package com.github.kdgaming0.enhancedstorage.gui;

import com.github.kdgaming0.enhancedstorage.storage.data.LayoutSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo and redo for one edit-mode session. It doesn't hook the edits themselves: the screen reports the current
 * layout whenever nothing is mid-change, and every difference from the last report becomes one step.
 */
public final class EditHistory {

    static final int LIMIT = 100;

    private final Deque<LayoutSnapshot> undo = new ArrayDeque<>();
    private final Deque<LayoutSnapshot> redo = new ArrayDeque<>();
    private LayoutSnapshot current;
    private @Nullable String notice;

    public EditHistory(LayoutSnapshot initial) {
        this.current = initial;
    }

    public void observe(LayoutSnapshot now) {
        if (!now.sameIgnoringCollapse(current)) push(now, null);
    }

    public void observe(LayoutSnapshot now, String label) {
        if (!now.sameIgnoringCollapse(current)) push(now, label);
    }

    public @Nullable LayoutSnapshot undo(LayoutSnapshot now) {
        observe(now);
        if (undo.isEmpty()) return null;
        redo.push(current);
        current = undo.pop();
        notice = null;
        return current;
    }

    public @Nullable LayoutSnapshot redo(LayoutSnapshot now) {
        observe(now);
        if (redo.isEmpty()) return null;
        undo.push(current);
        current = redo.pop();
        notice = null;
        return current;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public @Nullable String notice() {
        return notice;
    }

    public void dismissNotice() {
        notice = null;
    }

    private void push(LayoutSnapshot now, @Nullable String label) {
        undo.push(current);
        if (undo.size() > LIMIT) undo.removeLast();
        redo.clear();
        current = now;
        notice = label;
    }
}

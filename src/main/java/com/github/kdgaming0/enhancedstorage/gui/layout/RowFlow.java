package com.github.kdgaming0.enhancedstorage.gui.layout;

import com.github.kdgaming0.enhancedstorage.gui.StorageOverlayLayout;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs one row of the page grid. Pure geometry, so the overlay measures its width and places the
 * real cards with exactly the same rule.
 *
 * <p>Cells go left to right. When the next page is shorter than the row so far, the rest of the row's
 * width can become a sub-area that the following cells fill in lines, top to bottom, until the row's
 * height is used. It is only used when at least two lines fit. Reading order always matches list order.</p>
 */
public final class RowFlow {

    // A stack may make the row this much taller than its tallest page. Two default pages stacked
    // next to one narrow page miss a strict fit by a couple of pixels.
    static final int STACK_TOLERANCE = StorageOverlayLayout.SLOT_SIZE;

    private RowFlow() {
    }

    /**
     * Packs one row from {@code start}; never goes past {@code stop}, and always takes at least one cell.
     */
    public static Row pack(List<Box> boxes, int start, int stop, int limit, int spacing, int emptyHeight) {
        List<Integer> xs = new ArrayList<>();
        int used = 0;
        int tallest = 0;
        int i = start;
        while (i < stop) {
            Box box = boxes.get(i);
            int x = used == 0 ? 0 : used + spacing;
            if (used > 0 && x + box.width() > limit) break;

            if (used > 0 && canStack(box, tallest)) {
                Area area = fillArea(boxes, i, stop, x, limit - x, tallest + STACK_TOLERANCE, spacing, emptyHeight);
                if (area != null) {
                    int height = Math.max(tallest, area.height());
                    List<Spot> spots = topLine(xs, height);
                    spots.addAll(area.spots());
                    return new Row(area.end(), x + area.width(), height, spots);
                }
            }

            xs.add(x);
            used = x + box.width();
            tallest = Math.max(tallest, box.height());
            i++;
        }
        int height = tallest > 0 ? tallest : emptyHeight;
        return new Row(i, used, height, topLine(xs, height));
    }

    // Only a shorter page can start a sub-area; fillArea decides whether stacking is worth it.
    private static boolean canStack(Box box, int tallest) {
        return box.height() > 0 && box.height() < tallest;
    }

    private static List<Spot> topLine(List<Integer> xs, int height) {
        List<Spot> spots = new ArrayList<>(xs.size());
        for (int x : xs) spots.add(new Spot(x, 0, height));
        return spots;
    }

    /**
     * Fills the sub-area in lines; null unless at least two lines fit, since one line is just a normal row.
     */
    private static @Nullable Area fillArea(List<Box> boxes, int start, int stop, int areaX, int width,
                                           int maxHeight, int spacing, int emptyHeight) {
        List<Spot> spots = new ArrayList<>();
        int bottom = 0;
        int widest = 0;
        int lines = 0;
        int i = start;
        while (i < stop) {
            List<Integer> xs = new ArrayList<>();
            int used = 0;
            int tallest = 0;
            int j = i;
            while (j < stop) {
                Box box = boxes.get(j);
                int x = used == 0 ? 0 : used + spacing;
                // Unlike the top line, a cell wider than the area ends it rather than overflowing.
                if (x + box.width() > width) break;
                xs.add(x);
                used = x + box.width();
                tallest = Math.max(tallest, box.height());
                j++;
            }
            if (j == i) break;

            int lineY = i == start ? 0 : bottom + spacing;
            int lineHeight = tallest > 0 ? tallest : emptyHeight;
            if (lineY + lineHeight > maxHeight) break;

            for (int x : xs) spots.add(new Spot(areaX + x, lineY, lineHeight));
            bottom = lineY + lineHeight;
            widest = Math.max(widest, used);
            lines++;
            i = j;
        }
        return lines < 2 ? null : new Area(i, widest, bottom, spots);
    }

    /**
     * A cell's size. Height 0 means it has no height of its own and stretches to its line, like a spacer.
     */
    public record Box(int width, int height) {
    }

    /**
     * A cell's position relative to the row, and the height of the line it sits on.
     */
    public record Spot(int x, int y, int lineHeight) {
    }

    /**
     * Covers cells [start, end) of the input; spots are in the same order.
     */
    public record Row(int end, int width, int height, List<Spot> spots) {
    }

    private record Area(int end, int width, int height, List<Spot> spots) {
    }
}
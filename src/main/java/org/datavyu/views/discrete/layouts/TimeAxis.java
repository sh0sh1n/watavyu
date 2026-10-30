package org.datavyu.views.discrete.layouts;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Where times fall down a spreadsheet laid out in time. The weak temporal layout gives every onset and offset of a
 * visible cell a span in the sheet, spaced by the cells between them rather than by duration: it starts at the top
 * of the cells that start then and ends at the bottom of the cells that end then. A point cell's whole height is its
 * time's span, so the needle passes the cell at the instant of its time rather than crawling through it until the
 * next time. A time between two lies between the end of the first's span and the start of the next's in proportion.
 * Before the first time is the top of its span, after the last the end of its span.
 */
public final class TimeAxis {

    /** An axis for a spreadsheet without cells, or one not laid out in time: everything is at the top. */
    public static final TimeAxis NONE = new TimeAxis(new TreeMap<>());

    /** Where a time's cells start and end down the sheet. */
    private record Span(int top, int bottom) {
    }

    private final NavigableMap<Long, Span> spans = new TreeMap<>();

    /** The time whose span starts at each height: spans grow with times, so this is the same map turned round. */
    private final NavigableMap<Integer, Long> times = new TreeMap<>();

    /**
     * An axis on which each time has no height of its own.
     *
     * @param tops the height in the sheet of each time; later times must be lower down
     */
    public TimeAxis(final Map<Long, Integer> tops) {
        this(tops, Map.of());
    }

    /**
     * @param tops    the height in the sheet where the cells of each time start; later times must be lower down
     * @param bottoms the height where the cells that end at a time end, for times whose cells end below their top; a
     *                bottom past the next time's top is taken as that top, so the needle never moves back up
     */
    public TimeAxis(final Map<Long, Integer> tops, final Map<Long, Integer> bottoms) {
        Iterator<Map.Entry<Long, Integer>> each = new TreeMap<>(tops).entrySet().iterator();
        Map.Entry<Long, Integer> current = each.hasNext() ? each.next() : null;
        while (current != null) {
            Map.Entry<Long, Integer> next = each.hasNext() ? each.next() : null;
            int top = current.getValue();
            int bottom = Math.max(top, bottoms.getOrDefault(current.getKey(), top));
            if (next != null) {
                bottom = Math.min(bottom, next.getValue());
            }
            spans.put(current.getKey(), new Span(top, bottom));
            times.put(top, current.getKey());
            current = next;
        }
    }

    /**
     * @param time a time in milliseconds
     * @return its height in the sheet, in pixels from the top: the top of its span when cells start or end then
     */
    public int y(final long time) {
        Map.Entry<Long, Span> before = spans.floorEntry(time);
        Map.Entry<Long, Span> after = spans.ceilingEntry(time);
        if (before == null) {
            return after == null ? 0 : after.getValue().top();
        }
        if (before.getKey() == time) {
            return before.getValue().top();
        }
        if (after == null) {
            return before.getValue().bottom();
        }
        int from = before.getValue().bottom();
        double fraction = (double) (time - before.getKey()) / (after.getKey() - before.getKey());
        return from + (int) Math.round(fraction * (after.getValue().top() - from));
    }

    /**
     * @param y a height in the sheet, in pixels from the top
     * @return the time at that height, in milliseconds: a time over the whole of its span, above the first time the
     *         first, and below the last the last
     */
    public long time(final int y) {
        Map.Entry<Integer, Long> above = times.floorEntry(y);
        if (above == null) {
            return times.isEmpty() ? 0 : times.firstEntry().getValue();
        }
        long time = above.getValue();
        int from = spans.get(time).bottom();
        Map.Entry<Long, Span> next = spans.higherEntry(time);
        if (y <= from || next == null) {
            return time;
        }
        double fraction = (double) (y - from) / (next.getValue().top() - from);
        return time + Math.round(fraction * (next.getKey() - time));
    }

    /** @return whether the axis has no times, so every time is at the top */
    public boolean isEmpty() {
        return spans.isEmpty();
    }
}

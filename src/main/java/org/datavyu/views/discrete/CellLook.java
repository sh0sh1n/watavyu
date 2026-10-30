package org.datavyu.views.discrete;

import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How a cell looks, from where it stands against the needle and how it is selected. Time is shown without fills
 * (AGENTS.md › Decisions, U5): the cell in play is lifted a little and marked on its left edge with the accent, and
 * the times and values of a cell the needle has passed fade toward the cell colour by the {@code pastCellDim}
 * setting. Selection frames a cell, and a cell that overlaps the next one has a warning line along its bottom.
 */
public final class CellLook {

    /** Where a cell stands against the needle. */
    public enum Time {
        /** The needle has not reached it, or highlighting is off. */
        AHEAD,
        /** The needle is between its onset and offset. */
        IN_PLAY,
        /** The needle has passed it. */
        PAST
    }

    /** How a cell is selected. */
    public enum Selection {
        NONE,
        /** Selected by the user. */
        SELECTED,
        /** Highlighted, as the cell being edited is outside highlight-and-focus mode. */
        HIGHLIGHTED
    }

    /** Width of the frame of a selected cell, and of the padding inside an unselected one. */
    static final int FRAME = 3;

    /** Width of the accent mark on the left edge of the cell in play. */
    static final int MARK = 2;

    /**
     * The colours of a cell.
     *
     * @param background behind the cell
     * @param text       its values
     * @param time       its onset and offset
     * @param ordinal    its ordinal
     */
    public record Colours(Color background, Color text, Color time, Color ordinal) {
    }

    /** What decides a border. */
    private record Look(Palette palette, Selection selection, boolean overlap, boolean inPlay) {
    }

    /** Borders made for each look, so a clock tick sets the same border again rather than a new one. */
    private static final Map<Look, Border> BORDERS = new ConcurrentHashMap<>();

    private CellLook() {
    }

    /**
     * Returns where a cell stands against the needle: in play from its onset to its offset, as
     * {@code Cell.isInTimeWindow} has it, and past after the later of the two. A cell whose offset is before its
     * onset is never in play.
     *
     * @param onset     the cell's onset, in milliseconds
     * @param offset    its offset, which may be before its onset
     * @param now       the needle's time
     */
    public static Time timeOf(final long onset, final long offset, final long now) {
        if (now > Math.max(onset, offset)) {
            return Time.PAST;
        }
        return now >= onset && now <= offset ? Time.IN_PLAY : Time.AHEAD;
    }

    /**
     * Returns the cell's colours.
     *
     * @param palette    the palette
     * @param time       where the cell stands against the needle
     * @param dimPercent how far a past cell's text fades, from 0 to 100
     */
    public static Colours colours(final Palette palette, final Time time, final int dimPercent) {
        Color background = time == Time.IN_PLAY ? palette.inPlay() : palette.cell();
        if (time != Time.PAST) {
            return new Colours(background, palette.text(), palette.time(), palette.ordinal());
        }
        return new Colours(background, palette.fade(palette.text(), dimPercent),
                palette.fade(palette.time(), dimPercent), palette.fade(palette.ordinal(), dimPercent));
    }

    /**
     * Returns the cell's border; every combination keeps the same insets, so the cell's text never moves, and the same
     * combination gives the same border.
     *
     * @param palette   the palette
     * @param selection how the cell is selected
     * @param overlap   whether the cell overlaps the next one
     * @param inPlay    whether the needle is in the cell; a frame for selection hides the mark
     */
    public static Border border(final Palette palette, final Selection selection, final boolean overlap,
                                final boolean inPlay) {
        return BORDERS.computeIfAbsent(new Look(palette, selection, overlap, inPlay),
                look -> makeBorder(palette, selection, overlap, inPlay));
    }

    private static Border makeBorder(final Palette palette, final Selection selection, final boolean overlap,
                                     final boolean inPlay) {
        Border rule = new MatteBorder(selection == Selection.NONE ? 1 : 0, 0, 1, 0, palette.rule());
        Color bottom = overlap ? palette.warning() : null;
        Border frame;
        if (selection != Selection.NONE) {
            frame = new CompoundBorder(
                    new MatteBorder(0, 0, FRAME, 0, bottom == null ? palette.selection() : bottom),
                    new MatteBorder(FRAME, FRAME, 0, FRAME, palette.selection()));
        } else {
            Border left = inPlay
                    ? new CompoundBorder(new MatteBorder(0, MARK, 0, 0, palette.accent()),
                            new EmptyBorder(FRAME, FRAME - MARK, 0, FRAME))
                    : new EmptyBorder(FRAME, FRAME, 0, FRAME);
            frame = new CompoundBorder(
                    bottom == null ? new EmptyBorder(0, 0, FRAME, 0) : new MatteBorder(0, 0, FRAME, 0, bottom),
                    left);
        }
        // A selected cell has no top rule, so it gets one more pixel of frame room at the bottom of the rule.
        return selection == Selection.NONE ? new CompoundBorder(rule, frame)
                : new CompoundBorder(new CompoundBorder(rule, BorderFactory.createEmptyBorder(1, 0, 0, 0)), frame);
    }
}

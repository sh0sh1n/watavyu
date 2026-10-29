package org.datavyu.views.discrete;

import java.awt.Color;

/**
 * The colours of the application, one palette per macOS appearance (AGENTS.md › Decisions, U5). Paper, a light grey
 * well below screen white, is for lit rooms; Graphite is its dark twin for dim ones. Greys carry everything except
 * meaning: the accent marks the needle and the cell in play, the selection marks what is selected, and the warning
 * marks cells whose times are out of order. {@code PaletteTest} pins the contrast each pair must keep.
 *
 * @param name      shown in logs
 * @param dark      whether this palette is for the dark appearance
 * @param chrome    window furniture: the controls drawer, the playhead strip, column headers
 * @param ground    the sheet behind the cells
 * @param cell      a cell
 * @param inPlay    a cell the needle is in: a lift, not a fill
 * @param rule      lines between cells and columns
 * @param text      cell values
 * @param time      cell onsets and offsets
 * @param ordinal   cell ordinals
 * @param accent    the needle and the mark on the cell in play
 * @param selection the frame of a selected cell and a selected column's header
 * @param warning   cells whose times are out of order
 * @param surround  around a video, which stays the brightest thing on screen
 */
public record Palette(String name, boolean dark, Color chrome, Color ground, Color cell, Color inPlay, Color rule,
                      Color text, Color time, Color ordinal, Color accent, Color selection, Color warning,
                      Color surround) {

    /** For a Mac in light appearance. */
    public static final Palette PAPER = new Palette("Paper", false,
            hex("#d3d6d1"), hex("#dfe2dd"), hex("#e9ebe7"), hex("#eef0eb"), hex("#c2c6bf"),
            hex("#2b2c2a"), hex("#55574f"), hex("#81847d"),
            hex("#1a7a45"), hex("#448f63"), hex("#b8402e"), hex("#2a2b2a"));

    /** For a Mac in dark appearance. */
    public static final Palette GRAPHITE = new Palette("Graphite", true,
            hex("#161719"), hex("#1b1c1e"), hex("#222326"), hex("#272b2b"), hex("#303235"),
            hex("#d9dad5"), hex("#9a9c97"), hex("#6f716c"),
            hex("#5fcf86"), hex("#3f8f5e"), hex("#e0705f"), hex("#141516"));

    /**
     * Returns the palette for that appearance.
     *
     * @param dark whether the Mac is in dark appearance
     */
    public static Palette forAppearance(final boolean dark) {
        return dark ? GRAPHITE : PAPER;
    }

    /**
     * A colour faded toward this palette's cell colour, as the text of a cell the needle has passed.
     *
     * @param colour  the colour at full strength
     * @param percent how far to fade it, from 0 (unchanged) to 100 (the cell colour); values outside are clamped
     * @return the faded colour
     */
    public Color fade(final Color colour, final int percent) {
        double t = Math.max(0, Math.min(100, percent)) / 100.0;
        return new Color(
                mix(colour.getRed(), cell.getRed(), t),
                mix(colour.getGreen(), cell.getGreen(), t),
                mix(colour.getBlue(), cell.getBlue(), t));
    }

    private static int mix(final int from, final int to, final double t) {
        return (int) Math.round(from + (to - from) * t);
    }

    /**
     * Returns a colour's hex form, {@code #rrggbb}, as FlatLaf's theme properties take it.
     *
     * @param colour a colour
     */
    public static String toHex(final Color colour) {
        return String.format("#%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    private static Color hex(final String hex) {
        return Color.decode(hex);
    }
}

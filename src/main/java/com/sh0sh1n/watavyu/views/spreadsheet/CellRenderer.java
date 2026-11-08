package com.sh0sh1n.watavyu.views.spreadsheet;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.views.spreadsheet.editors.CellValuesView;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;

/**
 * Draws a cell, as {@code JTable} draws its cells: one component, set up for each cell in turn and painted where the
 * cell lies, rather than a component for every cell. Its parts are those of the cell being edited ({@link CellParts}),
 * laid out alike, so it also measures how tall each cell needs to be.
 */
final class CellRenderer extends JPanel {

    /** How tall a cell may be while it is measured: more than any cell's values need. */
    private static final int MEASURING_HEIGHT = 100_000;

    private final JLabel ordinal = new JLabel();

    private final JTextField onset = new TimeText();

    private final JTextField offset = new TimeText();

    private final JTextArea values = new JTextArea();

    private final CellParts parts;

    CellRenderer() {
        values.setLineWrap(true);
        values.setWrapStyleWord(true);
        values.setEditable(false);
        parts = new CellParts(this, ordinal, onset, offset, values);
    }

    /**
     * Sets the renderer up to draw a cell.
     *
     * @param cell    the cell
     * @param ordinal its place in its column, from 1
     * @param overlap whether it is marked as overlapping the next cell, or as ending before it starts
     * @param time    where it stands against the needle
     * @param dim     how far a past cell's text fades, from 0 to 100
     * @param highlightAndFocus whether highlight-and-focus mode is on
     */
    void configure(final Cell cell, final int ordinal, final boolean overlap, final CellLook.Time time,
                   final int dim, final boolean highlightAndFocus) {
        setText(cell);
        this.ordinal.setText(Integer.toString(ordinal));
        CellLook.Selection selection = CellLook.selectionOf(cell.isSelected(), cell.isHighlighted(),
                highlightAndFocus);
        parts.body.setBorder(CellLook.border(SpreadsheetTheme.PALETTE, selection, overlap,
                time == CellLook.Time.IN_PLAY));
        CellLook.Colours colours = CellLook.colours(SpreadsheetTheme.PALETTE, time, dim);
        parts.body.setBackground(colours.background());
        values.setForeground(colours.text());
        onset.setForeground(colours.time());
        offset.setForeground(colours.time());
        this.ordinal.setForeground(colours.ordinal());
    }

    /**
     * Returns how tall a cell needs to be: its times, and its values wrapped to the width.
     *
     * @param cell  the cell
     * @param width the cell's width
     */
    int height(final Cell cell, final int width) {
        setText(cell);
        ordinal.setText("1");
        setBounds(0, 0, width, MEASURING_HEIGHT);
        layOut(this);
        return getPreferredSize().height;
    }

    /**
     * Lays out a container and everything in it. {@code validate} would not: it does nothing in a container that has
     * never been on screen, as the renderer has not when it measures a cell before the sheet is first painted, and
     * the values would then wrap to whatever width they last had.
     */
    private static void layOut(final Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) {
                layOut(inner);
            }
        }
    }

    private void setText(final Cell cell) {
        values.setFont(SpreadsheetTheme.dataFont());
        values.setText(CellValuesView.textOf(cell));
        onset.setText(cell.getOnsetString());
        offset.setText(cell.getOffsetString());
    }

    /** Returns the text the renderer shows for a cell's values, as last set up. */
    String valuesText() {
        return values.getText();
    }

    /** The renderer is painted only through a cell renderer pane, which needs no repaints from it. */
    @Override
    public void repaint(final long tm, final int x, final int y, final int width, final int height) {
        // Nothing to repaint: the sheet paints the renderer where each cell lies.
    }

    /** A time, as the editor's time field shows it: a borderless field the width of its text. */
    private static final class TimeText extends JTextField {
        TimeText() {
            setBorder(null);
            setOpaque(false);
            setEditable(false);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = new Dimension(super.getPreferredSize());
            size.width += 2;
            return size;
        }

        @Override
        public Dimension getMaximumSize() {
            return super.getPreferredSize();
        }
    }
}

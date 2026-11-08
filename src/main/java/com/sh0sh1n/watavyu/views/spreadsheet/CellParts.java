package com.sh0sh1n.watavyu.views.spreadsheet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Box.Filler;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * The parts of a cell, laid out the same whether the cell is drawn ({@link CellRenderer}) or edited
 * ({@link SpreadsheetCell}), so a drawn cell and the editor over it are the same height: its ordinal, onset and offset
 * along the top, and its values below, wrapped to the cell's width, inside a border that shows how it is selected and
 * where it stands against the needle.
 */
final class CellParts {

    /** The space between the ordinal and the times, and between the two times. */
    private static final int TIME_SPACER = 5;

    /** The width a cell asks for: the widest a cell's times and a short value need. */
    static final int STRETCH = 229;

    /** The cell's body, inside its border, whose background shows the cell's colour. */
    final JPanel body = new JPanel(new BorderLayout());

    /** The row along the top, holding the ordinal and the times. */
    final JPanel top = new JPanel();

    /** Sets the width the cell asks for. */
    private final Filler stretcher = new Filler(new Dimension(STRETCH, 0), new Dimension(STRETCH, 0),
            new Dimension(STRETCH, 0));

    /**
     * Lays the parts out in a cell.
     *
     * @param cell    the cell's outermost panel
     * @param ordinal its ordinal
     * @param onset   its onset
     * @param offset  its offset
     * @param values  its values
     */
    CellParts(final JPanel cell, final JComponent ordinal, final JComponent onset, final JComponent offset,
              final JComponent values) {
        ordinal.setFont(SpreadsheetTheme.labelFont());
        ordinal.setForeground(SpreadsheetTheme.PALETTE.ordinal());
        for (JComponent time : new JComponent[] {onset, offset}) {
            time.setFont(SpreadsheetTheme.labelFont());
            time.setForeground(SpreadsheetTheme.PALETTE.time());
        }
        values.setFont(SpreadsheetTheme.dataFont());
        values.setForeground(SpreadsheetTheme.PALETTE.text());
        values.setOpaque(false);

        cell.setLayout(new BorderLayout());
        cell.add(new Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(Short.MAX_VALUE, 0)),
                BorderLayout.NORTH);
        cell.add(body, BorderLayout.CENTER);

        body.setBackground(SpreadsheetTheme.PALETTE.cell());
        body.setBorder(CellLook.border(SpreadsheetTheme.PALETTE, CellLook.Selection.NONE, false, false));

        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.add(ordinal);
        top.add(Box.createHorizontalStrut(TIME_SPACER));
        top.add(Box.createGlue());
        top.add(onset);
        top.add(Box.createHorizontalStrut(TIME_SPACER));
        top.add(offset);

        body.add(top, BorderLayout.NORTH);
        body.add(values, BorderLayout.CENTER);
        body.add(stretcher, BorderLayout.SOUTH);
    }
}

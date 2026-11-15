package com.sh0sh1n.watavyu.views.spreadsheet;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The keyboard's mode (COMMAND, INSERT or QUICK), shown above the playhead strip, level with the column headers, where
 * it never covers the needle. It shows nothing while keys type elsewhere. Whoever changes the mode repaints it.
 */
final class ModeMark extends JComponent {

    private static final long serialVersionUID = 1L;

    private static final Font FONT = SpreadsheetTheme.labelFont().deriveFont(Font.BOLD, 10F);

    private final transient Supplier<String> mode;
    private final transient IntSupplier height;

    /**
     * @param mode   the mode now, such as COMMAND, or empty for none
     * @param height the column headers' height, which this matches
     */
    ModeMark(final Supplier<String> mode, final IntSupplier height) {
        this.mode = mode;
        this.height = height;
        setName("mode");
    }

    /** Returns the mode shown. */
    String getMode() {
        return mode.get();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(PlayheadCarriage.WIDTH, height.getAsInt());
    }

    @Override
    protected void paintComponent(final Graphics graphics) {
        String text = mode.get();
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(SpreadsheetTheme.PALETTE.chrome());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(SpreadsheetTheme.PALETTE.rule());
            g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            if (text.isEmpty()) {
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(SpreadsheetTheme.PALETTE.time());
            g.setFont(FONT);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2,
                (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
        } finally {
            g.dispose();
        }
    }
}

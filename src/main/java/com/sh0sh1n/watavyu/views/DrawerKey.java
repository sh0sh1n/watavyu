package com.sh0sh1n.watavyu.views;

import com.sh0sh1n.watavyu.views.spreadsheet.SpreadsheetTheme;

import javax.swing.JButton;
import javax.swing.SwingConstants;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * A key of the controls drawer: a glyph with a short label under it, and in its top-left corner the key of the key map
 * that does the same, if one does.
 */
public final class DrawerKey extends JButton {

    /** Width and height of the glyph, in pixels. */
    static final int GLYPH_SIZE = 18;

    private static final Font LABEL_FONT = SpreadsheetTheme.uiFont(9.5F);

    private static final Font KEY_FONT = SpreadsheetTheme.labelFont().deriveFont(9F);

    private String key = "";

    /**
     * Makes a key, showing no key of the key map until {@link #showKey} is told one.
     *
     * @param glyph what the key shows
     * @param label what it does, in a word or two
     */
    public DrawerKey(final Glyph.Shape glyph, final String label) {
        super(label, Glyph.of(glyph, GLYPH_SIZE));
        setFont(LABEL_FONT);
        setVerticalTextPosition(SwingConstants.BOTTOM);
        setHorizontalTextPosition(SwingConstants.CENTER);
        setIconTextGap(1);
        setMargin(new Insets(0, 0, 0, 0));
        setFocusPainted(false);
    }

    /**
     * Shows the key of the key map that does the same, in the corner.
     *
     * @param mapped the key as shown, such as {@code [} or Space, or empty for none
     */
    public void showKey(final String mapped) {
        key = mapped;
        getAccessibleContext().setAccessibleDescription(mapped.isEmpty() ? null : "Key: " + mapped);
        repaint();
    }

    /** Returns what the key shows. */
    public Glyph.Shape getGlyph() {
        return ((Glyph) getIcon()).shape();
    }

    /**
     * Shows another glyph and label, as a key that does one of two things shows the one it will do.
     *
     * @param shape what the key shows
     * @param label what it does, in a word or two
     */
    public void show(final Glyph.Shape shape, final String label) {
        if (shape != getGlyph()) {
            setIcon(Glyph.of(shape, GLYPH_SIZE));
        }
        setText(label);
    }

    /** Returns the key of the key map shown in the corner, or empty for none. */
    public String getKey() {
        return key;
    }

    @Override
    protected void paintComponent(final Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(KEY_FONT);
            g2.setColor(SpreadsheetTheme.PALETTE.ordinal());
            g2.drawString(key, 4, 3 + g2.getFontMetrics().getAscent());
        } finally {
            g2.dispose();
        }
    }
}

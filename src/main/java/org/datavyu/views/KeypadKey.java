package org.datavyu.views;

import org.datavyu.views.discrete.SpreadsheetTheme;

import javax.swing.JButton;
import javax.swing.SwingConstants;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * A key of the controls drawer's keypad: a glyph with a short label under it, and in its top-left corner the keypad
 * key that does the same.
 */
public final class KeypadKey extends JButton {

    /** Width and height of the glyph, in pixels. */
    static final int GLYPH_SIZE = 18;

    private static final Font LABEL_FONT = SpreadsheetTheme.uiFont(9.5F);

    private static final Font KEY_FONT = SpreadsheetTheme.labelFont().deriveFont(9F);

    private final String key;

    /**
     * Makes a key.
     *
     * @param glyph what the key shows
     * @param label what it does, in a word or two
     * @param key   the keypad key that does the same, such as {@code 7} or {@code enter}
     */
    public KeypadKey(final Glyph.Shape glyph, final String label, final String key) {
        super(label, Glyph.of(glyph, GLYPH_SIZE));
        this.key = key;
        setFont(LABEL_FONT);
        setVerticalTextPosition(SwingConstants.BOTTOM);
        setHorizontalTextPosition(SwingConstants.CENTER);
        setIconTextGap(1);
        setMargin(new Insets(0, 0, 0, 0));
        setFocusPainted(false);
        getAccessibleContext().setAccessibleDescription("Keypad " + key);
    }

    /** Returns the keypad key shown in the corner. */
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

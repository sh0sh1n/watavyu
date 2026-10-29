/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.views;

import org.datavyu.util.DatavyuVersion;
import org.datavyu.views.discrete.SpreadsheetTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/**
 * The About window: the application's name and version, set in the palette and fonts rather than drawn from an
 * image (U5).
 */
public final class AboutV extends DatavyuDialog {

    /**
     * @param parent the main window
     * @param modal  whether the window blocks the others
     */
    public AboutV(final java.awt.Frame parent, final boolean modal) {
        super(parent, modal);
        setName(this.getClass().getSimpleName());
        setTitle("About Watavyu");
        setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SpreadsheetTheme.PALETTE.chrome());
        panel.setBorder(BorderFactory.createEmptyBorder(40, 64, 40, 64));
        panel.add(line("Watavyu", SpreadsheetTheme.headerFont().deriveFont(34F),
                SpreadsheetTheme.PALETTE.text()));
        panel.add(Box.createVerticalStrut(8));
        JLabel version = line(DatavyuVersion.getVersion(), SpreadsheetTheme.timeFont(13F),
                SpreadsheetTheme.PALETTE.accent());
        version.setName("version");
        panel.add(version);
        panel.add(Box.createVerticalStrut(16));
        panel.add(line("A personal fork of Datavyu", SpreadsheetTheme.uiFont(13F),
                SpreadsheetTheme.PALETTE.time()));
        setContentPane(panel);
        pack();
    }

    private static JLabel line(final String text, final Font font, final Color colour) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(colour);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }
}

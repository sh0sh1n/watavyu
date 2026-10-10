/**
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

import javax.swing.*;
import java.awt.*;


public class MacOSJFC extends BaseJFC {
    @Override
    protected JDialog createDialog(final Component parent) {
        JLabel pluginSelect = new JLabel();
        pluginSelect.setText("Plugin:");
        pluginSelect.setHorizontalAlignment(SwingConstants.RIGHT);

        pluginsBox = new JComboBox(plugins.toArray());
        pluginsBox.setEditable(false);
        pluginsBox.setLightWeightPopupEnabled(true);

        JPanel pluginPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        pluginPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        pluginPanel.add(pluginSelect);
        pluginPanel.add(pluginsBox);
        setAccessory(pluginPanel);

        JDialog dialog = super.createDialog(parent);
        dialog.pack();
        return dialog;
    }
}

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
package org.datavyu.views.discrete.datavalues;

import org.datavyu.models.db.*;
import org.datavyu.views.discrete.EditorComponent;

import javax.swing.text.JTextComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


/**
 * A Factory for creating data value editors.
 */
public class DataValueEditorFactory {

    /**
     * Constructor.
     */
    private DataValueEditorFactory() {
    }

    /**
     * Creates a vector of editor components that represent the matrix in a
     * data cell.
     *
     * @param ta The parent JTextComponent the editor is in.
     * @param c  The parent cell the editors resides within.
     * @return A vector of editor components to represent the matrix.
     */
    public static List<EditorComponent> buildMatrix(final JTextComponent ta,
                                                    final Cell c) {

        List<EditorComponent> eds = new ArrayList<EditorComponent>();

        MediaClip clip = c.getClip();
        if (clip != null) {
            eds.add(new FixedText(ta, clipLabel(clip)));
            return eds;
        }

        MatrixCellValue mv = (MatrixCellValue) c.getCellValue();
        if (mv != null) {
            eds.add(new FixedText(ta, "("));

            for (int i = 0; i < mv.getArguments().size(); i++) {
                eds.add(buildNominal(ta, (NominalCellValue) mv.getArguments().get(i)));

                if ((mv.getArguments().size() > 1) && (i < (mv.getArguments().size() - 1))) {
                    eds.add(new FixedText(ta, ", "));
                }
            }

            eds.add(new FixedText(ta, ")"));
        }

        return eds;
    }

    /**
     * Names what a media cell plays: its file's name, its frame rate when the file has video and has said it, and
     * whether the file is missing, as {@code cam.mp4 · 29.97 fps (missing)}.
     *
     * @param clip a media cell's clip
     * @return the cell's text
     */
    public static String clipLabel(final MediaClip clip) {
        String label = clip.fileName();
        if (clip.frameRate() > 0) {
            label += " \u00b7 " + new BigDecimal(clip.frameRate()).setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + " fps";
        }
        return Files.exists(Path.of(clip.file())) ? label : label + " (missing)";
    }

    /**
     * Creates a data value view from the specified data value within a matrix.
     *
     * @param ta The parent JTextComponent the editor is in.
     * @param v  The value this editor manipulates
     * @return An editor component to represent the specified data value.
     */
    public static EditorComponent buildNominal(final JTextComponent ta,
                                               final NominalCellValue v) {
        return new NominalDataValueEditor(ta, v);
    }
}

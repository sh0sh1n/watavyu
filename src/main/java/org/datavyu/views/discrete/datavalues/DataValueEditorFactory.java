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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;


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
     * @param quickKeyMode tells whether quick-key mode is on, in which typing does not change a value
     * @param typing       where what is typed goes, to change a value
     * @return A vector of editor components to represent the matrix.
     */
    public static List<EditorComponent> buildMatrix(final JTextComponent ta,
                                                    final Cell c,
                                                    final BooleanSupplier quickKeyMode,
                                                    final ValueTyping typing) {

        List<EditorComponent> eds = new ArrayList<EditorComponent>();

        MediaClip clip = c.getClip();
        if (clip != null) {
            eds.add(new FixedText(ta, clipLabel(clip, c.isClipFailed())));
            return eds;
        }

        List<Argument> codes = c.getVariable().getRootNode().childArguments;
        eds.add(new FixedText(ta, "("));
        for (int i = 0; i < codes.size(); i++) {
            int code = i;
            eds.add(buildNominal(ta, () -> c.getValue(code), () -> "<" + c.getVariable().getRootNode().childArguments.get(code).name + ">", quickKeyMode,
                value -> typing.typed(code, value)));
            if (i < codes.size() - 1) {
                eds.add(new FixedText(ta, ", "));
            }
        }
        eds.add(new FixedText(ta, ")"));

        return eds;
    }

    /**
     * Names what a media cell plays: its file's name, its frame rate when the file has video and has said it, and
     * whether the file is missing, as {@code cam.mp4 · 29.97 fps (missing)}, or there but could not be opened, as
     * {@code cam.mp4 (could not open)}.
     *
     * @param clip   a media cell's clip
     * @param failed whether the file could not be opened
     * @return the cell's text
     */
    public static String clipLabel(final MediaClip clip, final boolean failed) {
        String label = clip.fileName();
        if (clip.frameRate() > 0) {
            label += " \u00b7 " + new BigDecimal(clip.frameRate()).setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + " fps";
        }
        if (!Files.exists(Path.of(clip.file()))) {
            return label + " (missing)";
        }
        return failed ? label + " (could not open)" : label;
    }

    /**
     * Creates a data value view from the specified data value within a matrix.
     *
     * @param ta           The parent JTextComponent the editor is in.
     * @param value        the cell's value for the code, as it is now
     * @param placeholder  what an empty value shows
     * @param quickKeyMode tells whether quick-key mode is on, in which typing does not change the value
     * @param typing       where what is typed goes, to change the value
     * @return An editor component to represent the specified data value.
     */
    public static EditorComponent buildNominal(final JTextComponent ta,
                                               final Supplier<String> value,
                                               final Supplier<String> placeholder,
                                               final BooleanSupplier quickKeyMode,
                                               final Consumer<String> typing) {
        return new NominalDataValueEditor(ta, value, placeholder, quickKeyMode, typing);
    }
}

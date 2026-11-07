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
package com.sh0sh1n.watavyu.views.discrete.datavalues.vocabelements;

import com.sh0sh1n.watavyu.models.db.Column;
import com.sh0sh1n.watavyu.views.discrete.EditorComponent;
import com.sh0sh1n.watavyu.views.discrete.datavalues.FixedText;

import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * A Factory for creating data value editors.
 */
public class VocabElementEditorFactory {

    /**
     * Constructor.
     */
    private VocabElementEditorFactory() {
    }

    /**
     * Creates a vector of editor components that represent the matrix in a
     * data cell.
     *
     * @param ta The parent JTextComponent the editor is in.
     * @param var The column whose name and codes the editors show.
     * @param pv The parent VocabElementV the editor is in.
     * @return A vector of editor components to represent the element.
     */
    public static List<EditorComponent> buildVocabElement(final JTextComponent ta,
                                                          final Column var,
                                                          final VocabElementV pv) {

        List<EditorComponent> eds = new ArrayList<>();
        eds.add(new VENameEditor(ta, var, pv));
        eds.add(new FixedText(ta, "("));

        int numArgs = var.getCodes().size();
        // For each of the arguments, build a view representation
        for (int i = 0; i < numArgs; i++) {
            eds.addAll(buildFormalArg(ta, var, i, pv));
            if (numArgs > 1 && i < (numArgs - 1)) {
                eds.add(new FixedText(ta, ","));
            }
        }

        eds.add(new FixedText(ta, ")"));
        return eds;
    }

    /**
     * Creates a vector of editor components to represent an argument of a
     * data cell's matrix.
     *
     * @param ta The parent JTextComponent the editor is in.
     * @param var The column the code is in.
     * @param i  The index of the argument within the element.
     * @param pv The parent VocabElementV the editor is in.
     * @return A vector of editor components to represent the argument.
     */
    public static List<EditorComponent> buildFormalArg(JTextComponent ta,
                                                       Column var,
                                                       int i,
                                                       VocabElementV pv) {
        List<EditorComponent> eds = new ArrayList<>();

        eds.add(new FixedText(ta, "<"));
        eds.add(new FormalArgEditor(ta, var, i, pv));
        eds.add(new FixedText(ta, ">"));
        
        return eds;
    }
}

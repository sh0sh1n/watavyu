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
package org.datavyu.views.discrete.datavalues.vocabelements;

import org.datavyu.views.Glyph;
import org.datavyu.views.LockMark;
import org.datavyu.views.discrete.SpreadsheetTheme;
import org.datavyu.util.Resources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.models.db.Argument;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.Variable;
import org.datavyu.models.db.VariableListener;
import org.datavyu.views.VocabEditorV;
import org.datavyu.views.discrete.EditorComponent;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * A view for a vocab element.
 */
public class VocabElementV extends JPanel {

    /**
     * The width of icon to use in the vocab element view.
     */
    private static final int VE_WIDTH = 22;

    /**
     * The height of the icon to use in the vocab element view.
     */
    private static final int VE_HEIGHT = 22;

    /**
     * The dimensions to use for icons in the vocab element view.
     */
    private static final Dimension ICON_SIZE = new Dimension(VE_WIDTH,
            VE_HEIGHT);
    /**
     * Border adds a line across and a bit of space between
     */
    private static Border LINE_BORDER =
            BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, SpreadsheetTheme.PALETTE.rule()),
                    BorderFactory.createEmptyBorder(3, 0, 3, 0));
    /**
     * The logger for this class.
     */
    private static Logger LOGGER = LogManager.getLogger(VocabElementV.class);
    /**
     * The background of the code being edited.
     */
    private static final Color FOCUSED = SpreadsheetTheme.PALETTE.inPlay();
    /**
     * The label to use for if this vocab element has changed.
     */
    private JLabel invalidBangIcon;
    /**
     * The rootView of the VocabElement.
     */
    private VocabElementRootView veRootView;
    /**
     * The mark beside a name that cannot be applied.
     */
    private static final Icon WARNING_MARK = new Glyph(Glyph.Shape.WARNING, 14, SpreadsheetTheme.PALETTE.warning());
    private String bangIconText;
    /**
     * Is the name currently displayed for this column invalid
     */
    private boolean invalidState;
    /**
     * Is this vocab element view marked for removal?
     */
    private boolean deleteVE;
    /**
     * The underlying variable model that this vocab element view represents.
     */
    private Variable varModel;
    /**
     * The parent editor for this vocab element view.
     */
    private VocabEditorV parentEditor;
    /**
     * The mark beside a locked column's name, which shakes when the column refuses a change.
     */
    private final LockMark lockMark;
    /**
     * Hears of the column being locked and of changes it refuses, while this view is showing.
     */
    private final VariableListener lockListener = new VariableListener() {
        @Override public void nameChanged(final String newName) { }
        @Override public void visibilityChanged(final boolean isHidden) { }
        @Override public void cellInserted(final Cell newCell) { }
        @Override public void cellRemoved(final Cell deletedCell) { }
        @Override public void lockChanged(final boolean locked) { updateMark(); }
        @Override public void editRefused() { lockMark.shake(); }
    };

    public VocabElementV(Argument vocabArgument, Variable var, VocabEditorV vev) {

        Resources rMap = Resources.of(VocabElementV.class);

        bangIconText = rMap.getString("delta.tooltip");

        deleteVE = false;
        parentEditor = vev;
        varModel = var;

        invalidBangIcon = new JLabel();
        invalidBangIcon.setMaximumSize(ICON_SIZE);
        invalidBangIcon.setMinimumSize(ICON_SIZE);
        invalidBangIcon.setPreferredSize(ICON_SIZE);
        lockMark = new LockMark(14, invalidBangIcon);

        veRootView = new VocabElementRootView(var, this);

        JPanel leftPanel = new JPanel();
        FlowLayout flayout = new FlowLayout(FlowLayout.LEFT, 5, 0);
        leftPanel.setLayout(flayout);
        leftPanel.add(invalidBangIcon);
        veRootView.setOpaque(false);
        veRootView.setBackground(SpreadsheetTheme.PALETTE.cell());
        leftPanel.setOpaque(false);

        veRootView.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent fe) {
                if (!deleteVE) {
                    setBG(FOCUSED);
                }
            }

            @Override
            public void focusLost(FocusEvent fe) {
                if (!deleteVE) {
                    setBG(SpreadsheetTheme.PALETTE.cell());
                }
            }
        });

        BorderLayout layout = new BorderLayout();
        this.setLayout(layout);
        this.add(leftPanel, BorderLayout.WEST);
        this.add(veRootView, BorderLayout.CENTER);
        this.setBackground(SpreadsheetTheme.PALETTE.cell());
        this.setBorder(LINE_BORDER);

        this.rebuildContents();
        updateMark();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        varModel.addListener(lockListener);
    }

    @Override
    public void removeNotify() {
        varModel.removeListener(lockListener);
        super.removeNotify();
    }

    /** Returns the mark shown while the column is locked. */
    final LockMark getLockMark() {
        return lockMark;
    }

    /**
     * @return The parent dialog of this vocab element view.
     */
    public final VocabEditorV getParentDialog() {
        return parentEditor;
    }

    /**
     * Updates the display of the vocab element view by rebuilding its contents.
     */
    public final void rebuildContents() {
        veRootView.setVocabElement(varModel, this);
    }

    public final void setInvalid(final boolean invalid, final String lastValidName) {
        invalidState = invalid;
        updateMark();
        parentEditor.refreshNameWarnings();
    }

    /** Shows the warning beside a name that cannot be applied, or else the lock beside a locked column's name. */
    private void updateMark() {
        if (invalidState) {
            invalidBangIcon.setIcon(WARNING_MARK);
            invalidBangIcon.setToolTipText(bangIconText);
        } else if (varModel.isLocked()) {
            invalidBangIcon.setIcon(lockMark);
            invalidBangIcon.setToolTipText("Locked: unlock it from its header in the spreadsheet");
        } else {
            invalidBangIcon.setIcon(null);
            invalidBangIcon.setToolTipText(null);
        }
    }

    public boolean getInvalid() {
        return invalidState;
    }

    public String getCurrentNameDisplay() {
        return getNameComponent().getText();
    }

    /**
     * Gets a view for the supplied formal argument.
     *
     * @param fa The argument that we want a view for.
     * @return The view for the supplied formal argument if it exists, null
     * otherwise.
     */
    public final FormalArgEditor getArgumentView(final Argument fa) {
        for (EditorComponent ed : veRootView.getEditors()) {
            if (ed.getClass() == FormalArgEditor.class) {
                FormalArgEditor fArgEd = (FormalArgEditor) ed;

                if (fArgEd.getModel().equals(fa)) {
                    return fArgEd;
                }
            }
        }
        return null;
    }

    /**
     * @return The model (VocabElement) that this view represents.
     */
    public final Argument getModel() {
        return varModel.getRootNode();
    }

    /**
     * Replaces the model used for this vocab element view.
     *
     * @param vocabArgument The new model to use with this view.
     */
    public final void setModel(final Argument vocabArgument) {
        varModel.setRootNode(vocabArgument);
        this.rebuildContents();
    }

    public final Variable getVariable() {
        return varModel;
    }

    /**
     * @return The component for the name of the vocab element.
     */
    public final VENameEditor getNameComponent() {
        for (EditorComponent ed : veRootView.getEditors()) {
            if (ed.getClass() == VENameEditor.class) {
                return (VENameEditor) ed;
            }
        }
        return null;
    }

    /**
     * @return True if this component has focus, false otherwise.
     */
    @Override
    public final boolean hasFocus() {
        return veRootView.hasFocus();
    }

    /**
     * @return The view of the formal argument that has focus, null if no formal
     * argument view in this vocab element has focus.
     */
    public final FormalArgEditor getArgWithFocus() {
        EditorComponent ed = veRootView.getEdTracker().getCurrentEditor();
        if(ed.getClass() == FormalArgEditor.class)
            return (FormalArgEditor) ed;
        return null;
    }

    /**
     * @param argEd The arg editor to set focus on.
     */
    public final void requestArgFocus(FormalArgEditor argEd) {
        veRootView.getEdTracker().setEditor(argEd);
    }

    /**
     * @return True if this entire vocab element is flagged for deletion, false
     * otherwise.
     */
    public final boolean isDeletable() {
        return deleteVE;
    }

    /**
     * @return VocabElementRootView vocabElementRootView.
     */
    public final VocabElementRootView getDataView() {
        return veRootView;
    }

    public final void setBG(Color col) {
        this.setBackground(col);
    }

    @Override
    public final void requestFocus() {
        veRootView.requestFocus();
        veRootView.getEdTracker().setEditor(getNameComponent());
    }

    public final void requestFocus(VENameEditor veNEd) {
        veRootView.requestFocus();
        veRootView.getEdTracker().setEditor(veNEd);
    }
}

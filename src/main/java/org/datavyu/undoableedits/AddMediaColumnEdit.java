package org.datavyu.undoableedits;

import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/** Adding an empty media column, as Spreadsheet > New Media Column does. */
public final class AddMediaColumnEdit extends AbstractUndoableEdit {

    private final DataStore dataStore;
    private final String columnName;

    /**
     * Records a media column that has already been added.
     *
     * @param dataStore  the spreadsheet holding the column
     * @param columnName the column's name
     */
    public AddMediaColumnEdit(final DataStore dataStore, final String columnName) {
        this.dataStore = dataStore;
        this.columnName = columnName;
    }

    @Override
    public String getPresentationName() {
        return "New Media Column " + columnName;
    }

    @Override
    public void undo() throws CannotUndoException {
        Variable column = dataStore.getVariable(columnName);
        if (column == null || !column.isMedia() || !canUndo()) {
            throw new CannotUndoException();
        }
        super.undo();
        dataStore.removeVariable(column);
    }

    @Override
    public void redo() throws CannotRedoException {
        if (dataStore.getVariable(columnName) != null || !canRedo()) {
            throw new CannotRedoException();
        }
        super.redo();
        try {
            Variable column = dataStore.createMediaVariable(columnName);
            column.setOrderIndex(dataStore.getAllVariables().size() - 1);
        } catch (UserWarningException e) {
            throw new CannotRedoException();
        }
    }
}

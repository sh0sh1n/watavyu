package com.sh0sh1n.watavyu.undoableedits;

import com.sh0sh1n.watavyu.models.db.Cell;
import com.sh0sh1n.watavyu.models.db.Variable;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import java.util.List;
import java.util.Map;

/**
 * Deleting a code from a column, from the Code Editor. Undo puts the same code back in its place, with the value each
 * cell had for it.
 */
public final class DeleteCodeEdit extends AbstractUndoableEdit {

    private final Variable column;
    private final String code;
    private final int index;

    /** Each cell's value for the code, where it had one. */
    private final List<Map.Entry<Cell, String>> values;

    /**
     * Records a code about to be deleted, with its values.
     *
     * @param column the column
     * @param index  the code's place among the column's codes
     */
    public DeleteCodeEdit(final Variable column, final int index) {
        this.column = column;
        this.index = index;
        this.code = column.getCodes().get(index);
        this.values = column.getCells().stream()
            .filter(cell -> !cell.getValue(index).isEmpty())
            .map(cell -> Map.entry(cell, cell.getValue(index)))
            .toList();
    }

    @Override
    public String getPresentationName() {
        return "Delete Code " + code;
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        column.insertCode(index, code);
        for (Map.Entry<Cell, String> value : values) {
            value.getKey().setValue(index, value.getValue());
        }
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        column.removeCode(index);
    }
}

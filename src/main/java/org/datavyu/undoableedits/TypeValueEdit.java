package org.datavyu.undoableedits;

import org.datavyu.models.db.Cell;

import javax.swing.undo.UndoableEdit;
import java.util.List;

/**
 * Typing into a cell's values, a key at a time, which joins the typing it continues in the same cell
 * ({@link TypedEdit}): undo puts back each code's value from before the typing began. Undo and redo set the values
 * through the cell, so its view follows.
 */
public final class TypeValueEdit extends TypedEdit<List<String>> {

    /** The cell typed in, held itself, as its place in its column changes whenever the column is sorted by time. */
    private final Cell cell;

    /**
     * Records typing that has just changed a cell's values.
     *
     * @param cell       the cell
     * @param from       each code's value before, in the column's order of codes
     * @param to         each code's value now
     * @param continuing whether the typing continues the last, which it then joins
     */
    public TypeValueEdit(final Cell cell, final List<String> from, final List<String> to, final boolean continuing) {
        super(List.copyOf(from), List.copyOf(to), continuing);
        this.cell = cell;
    }

    @Override
    TypedEdit<List<String>> sameKind(final UndoableEdit edit) {
        return edit instanceof TypeValueEdit typing ? typing : null;
    }

    @Override
    boolean sameTarget(final TypedEdit<List<String>> other) {
        return ((TypeValueEdit) other).cell.equals(cell);
    }

    @Override
    void apply(final List<String> values) {
        // The codes are as they were when the typing was recorded: any change to them is an edit undone first.
        for (int i = 0; i < values.size(); i++) {
            cell.setMatrixValue(i, values.get(i));
        }
    }

    @Override
    public String getPresentationName() {
        return "Change Value in " + cell.getVariable().getName();
    }
}

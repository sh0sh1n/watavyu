package org.datavyu.undoableedits;

import org.datavyu.models.db.Variable;

/** Renaming a code, from the Code Editor, typed a key at a time. */
public final class RenameCodeEdit extends TypedRenameEdit {

    private final Variable column;
    private final int index;

    /**
     * Records a code that has just been renamed.
     *
     * @param column     the column
     * @param index      the code's place among the column's codes
     * @param from       its name before
     * @param continuing whether the rename continues the last one, which it then joins
     */
    public RenameCodeEdit(final Variable column, final int index, final String from, final boolean continuing) {
        super(from, column.getRootNode().childArguments.get(index).name, continuing);
        this.column = column;
        this.index = index;
    }

    @Override
    boolean sameTarget(final TypedRenameEdit other) {
        RenameCodeEdit code = (RenameCodeEdit) other;
        return code.column.equals(column) && code.index == index;
    }

    @Override
    void rename(final String name) {
        column.renameArgument(index, name);
    }

    @Override
    String noun() {
        return "Code";
    }
}

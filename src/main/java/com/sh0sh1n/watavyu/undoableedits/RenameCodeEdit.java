package com.sh0sh1n.watavyu.undoableedits;

import com.sh0sh1n.watavyu.models.Column;

/** Renaming a code, from the Code Editor, typed a key at a time. */
public final class RenameCodeEdit extends TypedRenameEdit {

    private final Column column;
    private final int index;

    /**
     * Records a code that has just been renamed.
     *
     * @param column     the column
     * @param index      the code's place among the column's codes
     * @param from       its name before
     * @param continuing whether the rename continues the last one, which it then joins
     */
    public RenameCodeEdit(final Column column, final int index, final String from, final boolean continuing) {
        super(from, column.getCodes().get(index), continuing);
        this.column = column;
        this.index = index;
    }

    @Override
    boolean sameTarget(final TypedEdit<String> other) {
        RenameCodeEdit code = (RenameCodeEdit) other;
        return code.column.equals(column) && code.index == index;
    }

    @Override
    void apply(final String name) {
        column.renameCode(index, name);
    }

    @Override
    String noun() {
        return "Code";
    }
}

package com.sh0sh1n.watavyu.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.UserWarningException;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.undoableedits.AddColumnEdit;
import com.sh0sh1n.watavyu.undoableedits.LockColumnEdit;
import com.sh0sh1n.watavyu.undoableedits.RemoveColumnEdit;
import com.sh0sh1n.watavyu.undoableedits.RenameColumnEdit;

import java.util.List;

/**
 * The commands that add, delete, rename, lock, hide and move one spreadsheet's columns. Each is the one path for its
 * user action, whether it comes from a menu, a column's header, the Column List or the Code Editor: it changes the
 * data model and records in the spreadsheet's undo history what is an edit there. The views follow the model.
 */
public final class ColumnCommands {

    private static final Logger logger = LogManager.getLogger(ColumnCommands.class);

    private final ProjectController spreadsheet;

    /**
     * @param spreadsheet the spreadsheet whose columns the commands change
     */
    ColumnCommands(final ProjectController spreadsheet) {
        this.spreadsheet = spreadsheet;
    }

    private DataStore dataStore() {
        return spreadsheet.getDataStore();
    }

    /**
     * New Column, from the spreadsheet's {@code +} button and the Code Editor: an empty annotation column after the
     * others, the one column selected and the one that New Cell next gives a cell.
     *
     * @param name the column's name
     * @return the new column
     * @throws UserWarningException if the name is taken or not allowed
     */
    public Column newColumn(final String name) throws UserWarningException {
        logger.info("New column " + name);
        Column column = dataStore().createColumn(name);
        dataStore().deselectAll();
        column.setSelected(true);
        spreadsheet.setLastCreatedColumn(column);
        spreadsheet.postEdit(new AddColumnEdit(dataStore(), column));
        return column;
    }

    /**
     * New Media Column, from the Spreadsheet menu: an empty media column after the others, the one column selected,
     * so media added next goes into it.
     *
     * @return the new column
     * @throws UserWarningException if the column cannot be added
     */
    public Column newMediaColumn() throws UserWarningException {
        Column column = MediaColumns.addSelected(dataStore());
        logger.info("New media column " + column.getName());
        spreadsheet.postEdit(new AddColumnEdit(dataStore(), column));
        return column;
    }

    /**
     * Delete Column, from a column's header and the Code Editor: deletes columns as one edit, unless any of them is
     * locked, when each locked one shows it and nothing is deleted. Selection is cleared.
     *
     * @param columns the columns to delete
     * @return whether they were deleted
     */
    public boolean delete(final List<Column> columns) {
        List<Column> deleted = columns.stream().filter(dataStore()::contains).distinct().toList();
        // Every locked column shakes its padlock, not just the first.
        if (deleted.isEmpty() || deleted.stream().filter(Column::refusesEdits).count() > 0) {
            return false;
        }
        logger.info("Delete columns " + deleted.stream().map(Column::getName).toList());
        RemoveColumnEdit edit = new RemoveColumnEdit(dataStore(), deleted);
        dataStore().deselectAll();
        deleted.forEach(dataStore()::removeColumn);
        spreadsheet.postEdit(edit);
        return true;
    }

    /**
     * Renames an annotation column, from the Code Editor, where a name is typed a key at a time: a rename continuing
     * the last one joins its edit, so undo puts back the name from before the typing began. Media columns keep the
     * names they are given.
     *
     * @param column     the column
     * @param name       its new name
     * @param continuing whether this continues the last rename, as each key typed into one name after the first does
     * @return whether it was renamed: its own name, or a column not in the spreadsheet, is no change
     * @throws UserWarningException if the column is a media column or locked, or the name is taken or not allowed
     */
    public boolean rename(final Column column, final String name, final boolean continuing)
        throws UserWarningException {
        if (column.getName().equals(name) || !dataStore().contains(column)) {
            return false;
        }
        if (column.isMedia()) {
            throw new UserWarningException("Media column " + column.getName() + " keeps the name it was given.");
        }
        if (dataStore().getColumn(name) != null) {
            throw new UserWarningException("Another column is named " + name + ".");
        }
        String from = column.getName();
        column.setName(name);
        spreadsheet.postEdit(new RenameColumnEdit(column, from, continuing));
        return true;
    }

    /**
     * Lock Column, from a column's header: locks or unlocks a column, as an edit, so undoing past a lock unlocks it.
     *
     * @param column the column
     * @param locked whether it is to be locked
     */
    public void setLocked(final Column column, final boolean locked) {
        if (column.isLocked() == locked || !dataStore().contains(column)) {
            return;
        }
        column.setLocked(locked);
        spreadsheet.postEdit(new LockColumnEdit(column, locked));
    }

    /**
     * Hide Column, from a column's header and the Column List: hides columns, which silences a media column's window
     * and deselects them and their cells ({@link Column#setHidden}). Hiding is not an edit.
     *
     * @param columns the columns to hide
     */
    public void hide(final List<Column> columns) {
        columns.forEach(column -> column.setHidden(true));
    }

    /**
     * Shows a hidden column, from the spreadsheet's hidden-columns menu and the Column List.
     *
     * @param column the column
     */
    public void show(final Column column) {
        column.setHidden(false);
    }

    /** Show All Columns, from the Column List. */
    public void showAll() {
        dataStore().getAllColumns().forEach(this::show);
    }

    /**
     * Moves a column to another's place, shifting those between, as dragging a header or the Code Editor's arrows do.
     * Locked columns move too. Moving is not an edit.
     *
     * @param column the column to move
     * @param to     the column whose place it takes
     */
    public void move(final Column column, final Column to) {
        int index = dataStore().indexOf(to);
        if (index >= 0 && dataStore().contains(column)) {
            dataStore().moveColumn(column, index);
        }
    }
}

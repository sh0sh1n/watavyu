/*
 * Copyright (c) 2011 OpenSHAPA Foundation, http://openshapa.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sh0sh1n.watavyu.models;

import java.util.List;

/**
 * An interface which abstracts away from the specific underlying database,
 * with methods to access columns and cells in an intuitive manner.
 */
public interface DataStore {

    /**
     * Returns the columns in spreadsheet order.
     *
     * @return a copy of the columns, in order
     */
    List<Column> getAllColumns();

    /**
     * Finds a column's place in the spreadsheet order.
     *
     * @param column a column
     * @return its place, or -1 if it is not in the spreadsheet
     */
    int indexOf(Column column);

    /**
     * Tells whether a column is in the spreadsheet: one remembered, or held by an undo edit, may have been taken out.
     *
     * @param column a column, or null
     * @return whether it is in the spreadsheet
     */
    default boolean contains(final Column column) {
        return column != null && indexOf(column) >= 0;
    }

    /**
     * Tells whether a cell is in the spreadsheet: one remembered, or held by an editor, may have been deleted, or gone
     * with its column. Only its own column is searched, so it is cheap enough for each key typed.
     *
     * @param cell a cell, or null
     * @return whether it is in the spreadsheet
     */
    default boolean contains(final Cell cell) {
        return cell != null && contains(cell.getColumn()) && cell.getColumn().contains(cell);
    }

    /**
     * Returns all the columns held in the datastore that are visible and not hidden.
     */
    List<Column> getVisibleColumns();

    /**
     * Returns the columns selected in the datastore.
     */
    List<Column> getSelectedColumns();

    /**
     * Clears the column selection - sets selected to false for every column
     * in the datastore.
     */
    void clearColumnSelection();

    /**
     * Returns all the selected cells, across the entire datastore.
     */
    List<Cell> getSelectedCells();

    /**
     * Clears the cell selection - sets selected to false for every cell in the
     * datastore.
     */
    void clearCellSelection();

    /**
     * Clear both the column and cell selections.
     */
    void deselectAll();

    /**
     * Returns the column that matches varName, NULL if unable to find a
     * column that matches varName.
     * @param varName The name of the column to fetch.
     */
    Column getColumn(String varName);

    /**
     * Finds the column holding a cell, which is the cell's own: a column takes no other's cells.
     *
     * @param cell a cell, or null
     * @return its column, or null if it is not in the spreadsheet ({@link #contains(Cell)})
     */
    default Column getColumn(final Cell cell) {
        return contains(cell) ? cell.getColumn() : null;
    }

    /**
     * Creates and adds a column to this datastore.
     *
     * @param name The name of the column to add to the datastore.
     *
     * @return The new column that was added to the datastore, with one code.
     * @throws UserWarningException if a column of that name exists, or the name breaks the naming rules
     */
    Column createColumn(final String name) throws UserWarningException;

    /**
     * Creates and adds a media column, with no clips. Its name is not held to the naming rules for new columns.
     *
     * @param name The name of the column.
     *
     * @return The new media column.
     * @throws UserWarningException if a column of that name exists
     */
    Column createMediaColumn(String name) throws UserWarningException;

    /**
     * Creates and adds a column to this datastore.
     *
     * @param name The name of the column to add to the datastore.
     * @param grandfathered Flag to exempt column from naming rules.
     *
     * @return The new column that was added to the datastore, with one code.
     * @throws UserWarningException if a column of that name exists, or the name breaks the naming rules and is not
     *                              exempt from them
     */
    Column createColumn(final String name, boolean grandfathered)
        throws UserWarningException;

    /**
     * Removes a column, then tells the listeners; nothing happens if the column is not in the spreadsheet.
     *
     * @param var the column
     */
    void removeColumn(final Column var);

    /**
     * Puts a column created earlier back at a place in the spreadsheet order, as undo and redo do, then tells the
     * listeners.
     *
     * @param var   the column
     * @param index its place, from 0 to the number of columns
     * @throws IllegalArgumentException if the spreadsheet has a column of the same name, or the place is out of range
     */
    void addColumn(Column var, int index);

    /**
     * Moves a column to another place in the spreadsheet order, shifting those between, then tells the listeners.
     *
     * @param var   a column in the spreadsheet
     * @param index its new place, from 0 to one less than the number of columns
     * @throws IllegalArgumentException if the column is not in the spreadsheet or the place is out of range
     */
    void moveColumn(Column var, int index);

    /**
     * Removes a column from the datastore.
     *
     * @param cell The cell to remove from the datastore.
     */
    void removeCell(final Cell cell);

    /**
     * Returns the name of the datastore.
     */
    String getName();

    /**
     * Mark this data store as unchanged; when all changes are committed.
     */
    void markAsUnchanged();

    /**
     * Mark this data store as changed when there are outstanding changes that need to be committed.
     */
    void markAsChanged();

    /**
     * Returns true if the data store has changed since it was last saved, false
     * otherwise.
     */
    boolean isChanged();

    /**
     * Sets the name of the datastore.
     *
     * @param datastoreName The new name to use for the datastore.
     */
    void setName(final String datastoreName);

    /**
     * Files a column under its new name, and tells the listeners if the name changed. A column out of the spreadsheet,
     * as undo leaves one, is not filed.
     *
     * @param oldname  the name the column had
     * @param newName  the name it has now
     * @param column the column
     */
    void updateColumnName(String oldname, String newName, Column column);

    /**
     * Sets the title notifier that needs to be informed when the title of the
     * application needs to be updated.
     */
    void setTitleNotifier(final TitleNotifier titleNotifier);

    /**
     * Adds a listener that needs to be notified when the datastore changes.
     */
    void addListener(final DataStoreListener listener);

    /**
     * Removes a listener from the list of things that need to be notified when
     * the datastore changes.
     */
    void removeListener(final DataStoreListener listener);

}

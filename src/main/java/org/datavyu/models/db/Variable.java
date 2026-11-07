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
package org.datavyu.models.db;

import java.util.List;

/**
 * Just a collection of cells, relates to a DataColumn.
 */
public interface Variable {

    /**
     * Puts a cell back into the column, as undo does.
     *
     * @param cell a cell made by this column
     */
    void addCell(final Cell cell);

    /**
     * Creates and inserts a cell from 0 to 0.
     *
     * @return the new cell
     * @throws IllegalStateException if this is a media column, whose cells are clips
     */
    Cell createCell();

    /**
     * Creates and inserts a point cell, whose onset and offset are both the given time. New cells are point cells.
     *
     * @param time The cell's onset and offset in milliseconds.
     * @return The newly created cell.
     */
    Cell createPointCell(long time);

    /**
     * Removes a cell from the variable.
     *
     * @param cell to remove from the variable.
     */
    void removeCell(final Cell cell);

    /**
     * Returns all the cells stored in the variable.
     */
    List<Cell> getCells();

    /**
     * Gets the 'index' cell from the variable that has been sorted temporally.
     *
     * @param index The index (from first onset to last offset) of the cell.
     * @return The cell.
     */
    Cell getCellTemporally(final int index);

    /**
     * Returns the names of the column's codes, in order; each cell has a value for each. A media column has none.
     */
    List<String> getCodes();

    /**
     * Gives a column without cells its codes, as reading a project file does, and numbers its next new code from
     * {@code code01} again. It is not an edit.
     *
     * @param codes the codes' names, in order
     * @throws IllegalStateException if the column has cells, or is a media column or locked
     */
    void setCodes(List<String> codes);

    /**
     * Returns all the cells stored in the variable using a temporal alignment.
     */
    List<Cell> getCellsTemporally();

    /**
     * Returns true if this variable contains the supplied cell, false otherwise.
     * @param c The cell to check if it exists in this variable.
     */
    boolean contains(final Cell c);

    /**
     * Returns true if the variable is currently selected or not.
     */
    boolean isSelected();

    /**
     * Selects the variable in the datastore. A hidden column is never selected, whatever asks: the selection is always
     * of what can be seen.
     *
     * @param selected True if the variable is selected, false otherwise.
     */
    void setSelected(final boolean selected);

    /**
     * Returns true if the variable is currently hidden or not.
     */
    boolean isHidden();

    /**
     * Hides or shows the column. Hiding deselects it and its cells and unhighlights them, however it is hidden (its
     * header, the Column List, closing its media window), so commands on the selection leave what cannot be seen alone.
     *
     * @param hidden True if the variable is hidden, false otherwise.
     */
    void setHidden(final boolean hidden);

    /**
     * Returns the name of the variable as a string.
     */
    String getName();

    /**
     * Sets the name of the variable.
     *
     * @param newName to set with this database.
     */
    void setName(final String newName) throws UserWarningException;

    /**
     * Sets the name of the variable.
     *
     * @param newName       the new name to set with this database.
     * @param grandfathered flag to exempt variable from naming rules
     */
    void setName(final String newName, final boolean grandfathered) throws UserWarningException;

    /**
     * Adds a code after the others, with an empty value in each cell. It is named {@code codeNN}, numbered after the
     * last code added (from {@code code01} for a column given its codes) and skipping names a code has.
     *
     * @return the new code's place among the column's codes, the last
     * @throws IllegalStateException if the column is a media column or locked
     */
    int addCode();

    /**
     * Puts a code back at a place among the others, with an empty value in each cell, as undoing its deletion does.
     *
     * @param index its place, from 0 to the number of codes
     * @param name  the code's name
     * @throws IllegalStateException if the column is locked
     * @throws IndexOutOfBoundsException if the place is out of range
     */
    void insertCode(int index, String name);

    /**
     * Moves a code and each cell's value for it to another place, shifting those between. A place out of range, or a
     * locked column, leaves the codes as they are.
     *
     * @param from the code's place
     * @param to   its new place
     */
    void moveCode(int from, int to);

    /**
     * Deletes a code and each cell's value for it, unless the column is locked.
     *
     * @param index the code's place
     */
    void removeCode(int index);

    /**
     * Renames a code, unless the column is locked. Any name is taken, as undo puts back the names a code has had;
     * {@code CodeCommands} and the Datavyu JSON import keep names to those a project file allows.
     *
     * @param index the code's place
     * @param name  its new name
     */
    void renameCode(int index, String name);

    /**
     * Adds a listener that needs to be notified when the variable changes.
     */
    void addListener(final VariableListener listener);

    /**
     * Removes a listener from the list of things that need to be notified when
     * the variable changes.
     */
    void removeListener(final VariableListener listener);

    DataStore getOwningDatastore();

    /**
     * Returns whether this is a media column, whose cells are clips made with {@link #createClip}; an annotation
     * column's cells are made with {@link #createCell}.
     */
    boolean isMedia();

    /**
     * A locked column refuses every change to its cells, its codes and its name, so a finished coding pass cannot be
     * changed by accident. A refused change does nothing, or throws {@link IllegalStateException} from a method that
     * would return what it made, and the column's listeners hear of it ({@link VariableListener#editRefused}).
     * Hiding, selecting, reordering and unlocking the column are not changes to it.
     *
     * @return whether the column is locked
     */
    boolean isLocked();

    /**
     * Locks or unlocks the column.
     *
     * @param locked whether the column refuses changes
     */
    void setLocked(boolean locked);

    /**
     * Asks whether the column refuses a change, as every place that starts one does first; if it does, its listeners
     * hear that a change was refused.
     *
     * @return whether the column is locked
     */
    boolean refusesEdits();

    /**
     * Adds a clip to a media column. Its offset is its onset plus the clip's duration.
     *
     * @param onset when the clip starts, in milliseconds
     * @param clip  what it plays
     * @return the clip's cell
     * @throws IllegalStateException if this is an annotation column
     */
    Cell createClip(long onset, MediaClip clip);
}

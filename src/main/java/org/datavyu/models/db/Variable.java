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
     * Adds a already formed cell to the variable
     *
     * @param cell
     */
    void addCell(final Cell cell);

    /**
     * Creates and inserts a cell into the variable.
     *
     * @return The newly created cell.
     */
    /**
     * @return a new cell from 0 to 0
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
     * @return All the cells stored in the variable.
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
     * @return The type of the variable.
     */
    Argument getRootNode();

    /**
     * Sets the variable type.
     *
     * @param newType The new argument to use with this variable.
     */
    void setRootNode(final Argument newType);

    /**
     * @return All the cells stored in the variable using a temporal alignment.
     */
    List<Cell> getCellsTemporally();

    /**
     * @param c The cell to check if it exists in this variable.
     * @return True if this variable contains the supplied cell, false otherwise.
     */
    boolean contains(final Cell c);

    /**
     * @return True if the variable is currently selected or not.
     */
    boolean isSelected();

    /**
     * Selects the variable in the datastore.
     *
     * @param selected True if the variable is selected, false otherwise.
     */
    void setSelected(final boolean selected);

    /**
     * @return True if the variable is currently hidden or not.
     */
    boolean isHidden();

    /**
     * Hides the variable in the datastore.
     *
     * @param hidden True if the variable is hidden, false otherwise.
     */
    void setHidden(final boolean hidden);

    /**
     * @return The name of the variable as a string.
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
     * Adds a code after the others, named {@code codeNN} after the codes added before it, with an empty value in each
     * cell.
     *
     * @return the new code
     * @throws IllegalStateException if the column is locked
     */
    Argument addArgument();

    /**
     * Puts a code back at a place among the others, with an empty value in each cell, as undoing its deletion does.
     *
     * @param code  the code
     * @param index its place, from 0 to the number of codes
     * @throws IllegalStateException if the column is locked
     */
    void insertArgument(Argument code, int index);

    /**
     * Moves a code and each cell's value for it to another place, shifting those between. A place out of range, or a
     * locked column, leaves the codes as they are.
     *
     * @param oldIndex the code's place
     * @param newIndex its new place
     */
    void moveArgument(int oldIndex, int newIndex);

    /**
     * Deletes a code and each cell's value for it, unless the column is locked.
     *
     * @param index the code's place
     */
    void removeArgument(int index);

    /**
     * Renames a code, unless the column is locked. Any name is taken, as undo puts back the names a code has had, which
     * a Datavyu import may have given it; {@code CodeCommands} keeps the names typed to those a project file allows.
     *
     * @param index the code's place
     * @param name  its new name
     */
    void renameArgument(int index, String name);

    /**
     * @param name a code's name
     * @return the place of the first code of that name, or -1 if there is none
     */
    int getArgumentIndex(String name);

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
     * @return whether this is a media column, whose cells are clips made with {@link #createClip}; an annotation
     * column's cells are made with {@link #createCell}
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

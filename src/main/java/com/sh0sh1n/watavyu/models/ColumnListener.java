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

/**
 * Implement this interface to be notified of changes to a column.
 */
public interface ColumnListener {

    /**
     * Called when the name of a column has changed.
     *
     * @param newName The new name of the column.
     */
    void nameChanged(final String newName);

    /**
     * Called when the visibility of a column has changed.
     *
     * @param isHidden The visibility state of the column, true if hidden,
     * false otherwise.
     */
    void visibilityChanged(final boolean isHidden);

    /**
     * A cell has been inserted into the column.
     *
     * @param newCell The new cell that has been added to the column.
     */
    void cellInserted(final Cell newCell);

    /**
     * A cell has been removed from the column.
     *
     * @param deletedCell The cell that has been removed from the column.
     */
    void cellRemoved(final Cell deletedCell);

    /**
     * Called when a code is added, deleted, moved or renamed, and each cell's values have followed.
     */
    default void codesChanged() {
    }

    /**
     * Called when a code is renamed, which changes no cell's value; by default as {@link #codesChanged}.
     *
     * @param index the code's place among the column's codes
     */
    default void codeRenamed(final int index) {
        codesChanged();
    }

    /**
     * The column has been locked or unlocked.
     *
     * @param locked whether the column is now locked
     */
    default void lockChanged(final boolean locked) {
    }

    /** A change to the column was refused because it is locked. */
    default void editRefused() {
    }
}

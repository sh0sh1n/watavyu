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
 * Implement this interface to be notified of changes to the datastore.
 */
public interface DataStoreListener {
    /**
     * Called when a column is added to the datastore.
     *
     * @param newColumn The column that was just added to the datastore.
     */
    void columnAdded(final Column newColumn);

    /**
     * Called when a column is removed from the datastore.
     *
     * @param deletedColumn The column that was removed from the datastore.
     */
    void columnRemoved(final Column deletedColumn);

    /**
     * Called when the order of columns in the datastore change.
     */
    void columnOrderChanged();
    
    /**
     * Called when a column is hidden.
     *
     * @param hiddenColumn The column that was hidden.
     */
    void columnHidden(final Column hiddenColumn);

    /**
     * Called when a column is made visible (opposite of hidden).
     *
     * @param visibleColumn  The column that was made visible.
     */
    void columnVisible(final Column visibleColumn);

    /**
     * Called when the name of a column has changed.
     *
     * @param editedColumn The column that has had its name changed.
     */
    void columnNameChange(final Column editedColumn);
}

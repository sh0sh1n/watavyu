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
 * Represents a cell in the OpenSHAPA spreadsheet.
 */
public interface Cell {

    /**
     * Returns the offset timestamp in a HH:mm:ss:SSS format, where HH is 24 hour mm is minutes in an hour, ss is
     * seconds in a minute and SSS is milliseconds in a second.
     */
    String getOffsetString();

    /** Returns the offset in milliseconds. */
    long getOffset();

    /**
     * Sets the offset for this cell.
     *
     * @param newOffset The new offset timestamp in milliseconds to use for this cell.
     * @throws IllegalArgumentException if the offset is negative, as no time is
     */
    void setOffset(final long newOffset);

    /**
     * Sets the offset for this cell.
     *
     * @param newOffset The new onset timestamp for this cell in string in the
     *                  format "HH:MM:SS:mmm" where HH = hours, MM = minutes, SS = seconds and
     *                  mmm = milliseconds.
     */
    void setOffset(final String newOffset);

    /** Returns the onset in milliseconds. */
    long getOnset();

    /**
     * Sets the onset for this cell.
     *
     * @param newOnset The new onset timestamp for this cell in string in the
     *                 format "HH:MM:SS:mmm" where HH = hours, MM = minutes, SS = seconds and
     *                 mmm = milliseconds.
     */
    void setOnset(final String newOnset);

    /**
     * Sets the onset for this cell.
     *
     * @param newOnset The new onset timestamp in milliseconds to use for this
     *                 cell.
     * @throws IllegalArgumentException if the onset is negative, as no time is
     */
    void setOnset(final long newOnset);

    /**
     * Returns the onset timestamp in a HH:mm:ss:SSS format, where HH is 24 hour
     * mm is minutes in an hour, ss is seconds in a minute and SSS is
     * milliseconds in a second.
     */
    String getOnsetString();

    /**
     * Returns the cell's values, one for each of its column's codes in their order, {@code ""} for an empty one; a
     * clip has none.
     */
    List<String> getValues();

    /**
     * Returns the cell's value for one code.
     *
     * @param code a code's place among the column's codes
     * @return the cell's value for the code, {@code ""} if empty
     * @throws IndexOutOfBoundsException if the column has no such code
     */
    String getValue(int code);

    /**
     * Sets the cell's value for one code, and tells the cell's listeners if it changed. A locked column refuses it.
     *
     * @param code  a code's place among the column's codes
     * @param value the value, {@code ""} to empty it
     * @throws IndexOutOfBoundsException if the column has no such code
     * @throws NullPointerException      if the value is null
     */
    void setValue(int code, String value);

    /**
     * Sets all the cell's values, and tells the cell's listeners once if any changed. A locked column refuses it.
     *
     * @param values a value for each code, in their order
     * @throws IllegalArgumentException if there is not one value for each code
     * @throws NullPointerException      if a value is null
     */
    void setValues(List<String> values);

    /** Returns the cell's column. */
    Variable getVariable();

    /**
     * Returns true if the cell is selected, false otherwise.
     */
    boolean isSelected();

    /**
     * Selects this cell. A cell in a hidden column is never selected, whatever asks, and one put back into a hidden
     * column, as by an undo, is deselected: the selection is always of what can be seen.
     *
     * @param selected True if this cell is selected, false if unselected
     */
    void setSelected(final boolean selected);

    /**
     * Returns true if the cell is highlighted, false otherwise.
     */
    boolean isHighlighted();

    /**
     * Highlights the cell.
     *
     * @param highlighted True if this cell is highlighted, false otherwise.
     */
    void setHighlighted(final boolean highlighted);

    /** Adds a listener to be told when the cell changes. */
    void addListener(final CellListener cellListener);

    /** Removes a listener added with {@link #addListener}. */
    void removeListener(final CellListener cellListener);

    /**
     * Returns whether a time falls between the cell's onset and offset, both included. A cell whose offset is before its
     * onset holds no time.
     *
     * @param time a time in milliseconds
     * @return whether the cell spans it
     */
    boolean isInTimeWindow(long time);

    /**
     * Returns what a media column's cell plays, or null for an annotation column's cell.
     */
    MediaClip getClip();

    /**
     * Changes what a media column's cell plays, as when its file opens and says how long it is. The offset follows.
     *
     * @param clip what the cell plays
     * @throws IllegalStateException if this is an annotation column's cell
     */
    void setClip(MediaClip clip);

    /** What the player last found of a media cell's file. It is the session's state, not saved. */
    enum ClipFile {
        /** There, or not looked for yet */
        FOUND,
        /** Not there */
        MISSING,
        /** There, but it could not be opened */
        FAILED
    }

    /**
     * Returns what the player last found of this cell's media file: the file is looked for when its clip is opened,
     * as when the project opens or the clip is added, and not each time the cell is drawn.
     */
    ClipFile getClipFile();

    /**
     * Records what the player found of this cell's media file, and tells the cell's listeners, so its text says so.
     *
     * @param found what it found
     * @throws IllegalStateException if this is an annotation column's cell
     */
    void setClipFile(ClipFile found);
}

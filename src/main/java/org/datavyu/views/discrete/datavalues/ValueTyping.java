package org.datavyu.views.discrete.datavalues;

/** Where a cell's value editors send what is typed, so a command changes the value and records it. */
@FunctionalInterface
public interface ValueTyping {

    /**
     * Tells of a code's value typed.
     *
     * @param code  the code's place among the column's codes
     * @param value its new value, {@code ""} to empty it
     */
    void typed(int code, String value);
}

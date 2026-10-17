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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A column's codes, or one code. A column's root argument holds its codes as children, in order; a code has none.
 */
public final class Argument implements Serializable {

    // The name of argument
    public String name;

    // The child arguments
    public List<Argument> childArguments;

    // The Identifier of this variable
    public long id;

    private int lastCodeNumber = 0;

    /**
     * Constructor.
     *
     * @param newName The name of the code, or of the column's root
     */
    public Argument(final String newName) {
        id = this.hashCode();
        name = newName;
        childArguments = new ArrayList<>();
    }

    /**
     * Makes a column's root argument holding one code, {@code code01}, as a new column has.
     *
     * @param name the root's name
     * @return the root
     */
    public static Argument column(final String name) {
        Argument root = new Argument(name);
        root.addChildArgument();
        return root;
    }

    protected Argument addChildArgument() {
        Argument child = new Argument(String.format("code%02d", lastCodeNumber + 1));
        lastCodeNumber++;
        childArguments.add(child);
        return child;
    }

    public void clearChildArguments() {
        childArguments.clear();
    }

    public boolean equals(Argument other) {
        return  other.id == this.id;
    }
}

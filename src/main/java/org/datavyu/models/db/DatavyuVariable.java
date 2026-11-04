/*
 * Copyright (c) 2011 Datavyu Foundation, http://datavyu.org
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maps a Datavyu variable to a data store.
 */
public final class DatavyuVariable implements Variable {

    private static Logger logger = LogManager.getLogger(DatavyuVariable.class);
    /** Told of changes to this column. */
    private final List<VariableListener> listeners = new CopyOnWriteArrayList<>();
    private static CellComparator CellComparator = new CellComparator();
    final private UUID variableId = UUID.randomUUID();
    private List<Cell> cells = new CopyOnWriteArrayList<>();
    private Argument rootNodeArgument = null;
    private Boolean selected;
    private Boolean hidden;
    private String name;
    private DatavyuDataStore owningDatastore;
    private boolean media;
    private boolean locked;

    /**
     * Default constructor.
     */
    public DatavyuVariable() {}

    /**
     * Constructor.
     * @param name          The name to use for the variable being constructed.
     * @param type          The codes of the variable; a media column has none.
     * @param grandfathered Flag to exempt variable from naming rules.
     * @param dds           The datastore to which this variable belongs
     * @param media         Whether this is a media column
     */
    DatavyuVariable(String name, Argument type, boolean grandfathered, DatavyuDataStore dds, boolean media)
            throws UserWarningException {
        this.media = media;
        owningDatastore = dds;
        this.setName(name, grandfathered);
        this.setRootNode(type);
        this.setHidden(false);
        this.setSelected(true);
        owningDatastore.markAsChanged();
    }


    @Override

    public void addCell(Cell cell) {
        if (refusesEdits()) {
            return;
        }
        if (cell.getCellValue().getArgument() == this.getRootNode()) {
            cells.add(cell);
            for (VariableListener vl : listeners) {
                vl.cellInserted(cell);
            }
            owningDatastore.markAsChanged();
        }
    }

    @Override

    public DataStore getOwningDatastore() {
        return owningDatastore;
    }


    /**
     * @return The internal Identifier for this variable.
     */
    public UUID getID() {
        return variableId;
    }

    @Override
    public Cell createCell() {
        if (media) {
            throw new IllegalStateException("The cells of media column " + name + " are clips");
        }
        requireUnlocked();
        Cell c = new DatavyuCell(this, this.getRootNode());

        cells.add(c);

        for (VariableListener vl : listeners) {
            vl.cellInserted(c);
        }

        owningDatastore.markAsChanged();
        return c;
    }

    @Override
    public Cell createClip(final long onset, final MediaClip clip) {
        if (!media) {
            throw new IllegalStateException("Annotation column " + name + " has no clips");
        }
        requireUnlocked();
        Cell c = new DatavyuCell(this, this.getRootNode(), onset, clip);
        cells.add(c);
        for (VariableListener vl : listeners) {
            vl.cellInserted(c);
        }
        owningDatastore.markAsChanged();
        return c;
    }

    @Override
    public boolean isMedia() {
        return media;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public void setLocked(final boolean newLocked) {
        if (newLocked != locked) {
            locked = newLocked;
            owningDatastore.markAsChanged();
            for (VariableListener vl : listeners) {
                vl.lockChanged(newLocked);
            }
        }
    }

    @Override
    public boolean refusesEdits() {
        if (locked) {
            for (VariableListener vl : listeners) {
                vl.editRefused();
            }
        }
        return locked;
    }

    /** Refuses a change that would return what it made. */
    private void requireUnlocked() {
        if (refusesEdits()) {
            throw new IllegalStateException("Column " + name + " is locked");
        }
    }

    @Override
    public Cell createPointCell(final long time) {
        Cell cell = createCell();
        cell.setOnset(time);
        cell.setOffset(time);
        return cell;
    }

    @Override
    public void removeCell(final Cell cell) {
        if (refusesEdits()) {
            return;
        }
        cells.remove(cell);

        owningDatastore.markAsChanged();

        for (VariableListener vl : listeners) {
            vl.cellRemoved(cell);
        }

    }

    @Override
    public List<Cell> getCells() {
        return cells;
    }

    @Override
    public Cell getCellTemporally(final int index) {
        Collections.sort(cells, CellComparator);
        return cells.get(index);
    }

    @Override
    public Argument getRootNode() {
        return rootNodeArgument;
    }

    @Override
    public void setRootNode(final Argument a) {
        if (refusesEdits()) {
            return;
        }
        owningDatastore.markAsChanged();
        rootNodeArgument = a;
    }

    @Override
    public List<Cell> getCellsTemporally() {
        Collections.sort(cells, CellComparator);
        return cells;
    }

    @Override
    public boolean contains(final Cell c) {
        return cells.contains(c);
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setSelected(final boolean selected) {
        this.selected = selected;
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public void setHidden(final boolean hiddenParm) {
        if (hidden == null || hiddenParm != hidden) {
            owningDatastore.markAsChanged();
            hidden = hiddenParm;
            if (hidden) {
                // What cannot be seen is not selected, so commands on the selection leave it alone.
                setSelected(false);
                for (Cell cell : getCells()) {
                    cell.setSelected(false);
                    cell.setHighlighted(false);
                }
            }

            for (VariableListener vl : listeners) {
                vl.visibilityChanged(hidden);
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String newName) throws UserWarningException {
        this.setName(newName, false);
    }


    @Override


    public void setName(final String newName, boolean grandfathered) throws UserWarningException {
        // Pre-conditions, the newName must have at least one character.
        if (newName.length() < 1) {
            throw new UserWarningException("Unable to add column, a name must be supplied.");
        }
        if (refusesEdits()) {
            throw new UserWarningException("Column " + name + " is locked: unlock it to rename it.");
        }

        // Pre-conditions, check to make sure newName doesn't contain invalid chars or begin with a number or underscore
        if (!grandfathered && !isNameValid(newName)) {
            throw new UserWarningException("Unable to add column:\n\tOnly alphanumeric characters and underscore are permitted.\n\tName must begin with a letter\n\tMust contain fewer than 255 characters");
        }

        // The name is set first, so the data store's listeners see the new one.
        String oldName = name;
        this.name = newName;
        if (oldName != null && owningDatastore != null) {
            owningDatastore.updateVariableName(oldName, newName, this);
        }
        for (VariableListener vl : listeners) {
            vl.nameChanged(newName);
        }
    }


    private boolean isNameValid(String nameCandidate) {
        return nameCandidate != null && nameCandidate.matches("[a-zA-Z][a-zA-Z0-9_]*") && nameCandidate.length() < 255;
    }

    @Override
    public Argument addArgument() {
        requireUnlocked();
        Argument child = getRootNode().addChildArgument();
        for (Cell cell : getCells()) {
            cell.addMatrixValue(child);
        }
        codesChanged();
        return child;
    }

    @Override
    public void insertArgument(final Argument code, final int index) {
        requireUnlocked();
        List<Argument> codes = getRootNode().childArguments;
        if (index < 0 || index > codes.size()) {
            throw new IndexOutOfBoundsException("Column " + name + " has no place " + index + " for a code");
        }
        codes.add(index, code);
        for (Cell cell : getCells()) {
            cell.addMatrixValue(code);
            cell.moveMatrixValue(codes.size() - 1, index);
        }
        codesChanged();
    }

    @Override
    public void moveArgument(final int oldIndex, final int newIndex) {
        List<Argument> codes = getRootNode().childArguments;
        if (refusesEdits() || oldIndex < 0 || oldIndex >= codes.size() || newIndex < 0 || newIndex >= codes.size()) {
            return;
        }
        codes.add(newIndex, codes.remove(oldIndex));
        for (Cell cell : getCells()) {
            cell.moveMatrixValue(oldIndex, newIndex);
        }
        codesChanged();
    }

    @Override
    public void removeArgument(final int index) {
        if (refusesEdits()) {
            return;
        }
        getRootNode().childArguments.remove(index);
        for (Cell cell : getCells()) {
            cell.removeMatrixValue(index);
        }
        codesChanged();
    }

    @Override
    public void renameArgument(final int index, final String newName) {
        if (refusesEdits()) {
            return;
        }
        getRootNode().childArguments.get(index).name = newName;
        owningDatastore.markAsChanged();
        for (VariableListener vl : listeners) {
            vl.codeRenamed(index);
        }
    }

    /** Marks the project changed and tells the views that the codes have, so they redraw names and values. */
    private void codesChanged() {
        owningDatastore.markAsChanged();
        for (VariableListener vl : listeners) {
            vl.codesChanged();
        }
    }

    @Override
    public int getArgumentIndex(final String name) {
        Argument arg = getRootNode();
        for (int i = 0; i < arg.childArguments.size(); i++) {
            if (arg.childArguments.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void addListener(final VariableListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(final VariableListener listener) {
        listeners.remove(listener);
    }


    //would like to change the above calls to DatavyuDataStore.markAsChanged to this,
    //but am holding off for now to avoid merge complications
}
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

package com.sh0sh1n.watavyu.models.db;


import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maps a Datavyu variable to a data store.
 */
public final class DatavyuVariable implements Variable {

    /** Told of changes to this column. */
    private final List<VariableListener> listeners = new CopyOnWriteArrayList<>();
    /** Cells in time order: by onset, then by offset. */
    private static final Comparator<Cell> CELL_ORDER =
        Comparator.comparingLong(Cell::getOnset).thenComparingLong(Cell::getOffset);
    final private UUID variableId = UUID.randomUUID();
    private List<Cell> cells = new CopyOnWriteArrayList<>();
    /** The codes' names, in order; each cell has a value for each. */
    private final List<String> codes = new ArrayList<>();
    /** The number in the name of the last {@code codeNN} code added, which a new code's number follows. */
    private int lastCodeNumber;
    private Boolean selected;
    private Boolean hidden;
    private String name;
    private DatavyuDataStore owningDatastore;
    private boolean media;
    private boolean locked;

    /**
     * Constructor.
     * @param name          The name to use for the variable being constructed.
     * @param grandfathered Flag to exempt variable from naming rules.
     * @param dds           The datastore to which this variable belongs
     * @param media         Whether this is a media column
     */
    DatavyuVariable(String name, boolean grandfathered, DatavyuDataStore dds, boolean media)
            throws UserWarningException {
        this.media = media;
        owningDatastore = dds;
        this.setName(name, grandfathered);
        // An annotation column starts with one code; a media column has none.
        if (!media) {
            codes.add(nextCodeName());
        }
        this.setHidden(false);
        this.setSelected(true);
        owningDatastore.markAsChanged();
    }


    @Override

    public void addCell(Cell cell) {
        if (refusesEdits()) {
            return;
        }
        if (equals(cell.getVariable())) {
            if (isHidden()) {
                // As an undo puts back a cell that was selected.
                cell.setSelected(false);
            }
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
     * Returns the internal Identifier for this variable.
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
        Cell c = new DatavyuCell(this, codes.size());

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
        Cell c = new DatavyuCell(this, onset, clip);
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
        cells.sort(CELL_ORDER);
        return cells.get(index);
    }

    @Override
    public List<String> getCodes() {
        return List.copyOf(codes);
    }

    @Override
    public void setCodes(final List<String> newCodes) {
        if (!cells.isEmpty() || media) {
            throw new IllegalStateException("Column " + name + " cannot be given codes: it has cells, or is media");
        }
        requireUnlocked();
        codes.clear();
        codes.addAll(newCodes);
        // A new code is numbered from code01 again, skipping the names given.
        lastCodeNumber = 0;
        codesChanged();
    }

    @Override
    public List<Cell> getCellsTemporally() {
        cells.sort(CELL_ORDER);
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
        this.selected = selected && !isHidden();
    }

    @Override
    public boolean isHidden() {
        // Unset until the constructor sets it.
        return Boolean.TRUE.equals(hidden);
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
    public int addCode() {
        if (media) {
            throw new IllegalStateException("Media column " + name + " has no codes");
        }
        requireUnlocked();
        codes.add(nextCodeName());
        int index = codes.size() - 1;
        ownCells().forEach(cell -> cell.insertValue(index));
        codesChanged();
        return index;
    }

    /** Names a new code {@code codeNN}, numbered after the last added, skipping names taken, as one read from a file. */
    private String nextCodeName() {
        String code;
        do {
            lastCodeNumber++;
            code = String.format("code%02d", lastCodeNumber);
        } while (codes.contains(code));
        return code;
    }

    @Override
    public void insertCode(final int index, final String code) {
        requireUnlocked();
        codes.add(index, code);
        ownCells().forEach(cell -> cell.insertValue(index));
        codesChanged();
    }

    @Override
    public void moveCode(final int from, final int to) {
        if (refusesEdits() || from < 0 || from >= codes.size() || to < 0 || to >= codes.size()) {
            return;
        }
        codes.add(to, codes.remove(from));
        ownCells().forEach(cell -> cell.moveValue(from, to));
        codesChanged();
    }

    @Override
    public void removeCode(final int index) {
        if (refusesEdits()) {
            return;
        }
        codes.remove(index);
        ownCells().forEach(cell -> cell.removeValue(index));
        codesChanged();
    }

    @Override
    public void renameCode(final int index, final String newName) {
        if (refusesEdits()) {
            return;
        }
        codes.set(index, newName);
        owningDatastore.markAsChanged();
        for (VariableListener vl : listeners) {
            vl.codeRenamed(index);
        }
    }

    /** Returns the column's cells, each made by it ({@link #addCell} takes back only its own). */
    private List<DatavyuCell> ownCells() {
        return cells.stream().map(DatavyuCell.class::cast).toList();
    }

    /** Marks the project changed and tells the views that the codes have, so they redraw names and values. */
    private void codesChanged() {
        owningDatastore.markAsChanged();
        for (VariableListener vl : listeners) {
            vl.codesChanged();
        }
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
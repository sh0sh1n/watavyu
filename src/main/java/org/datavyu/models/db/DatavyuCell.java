/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.models.db;

import org.datavyu.util.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;


public class DatavyuCell implements Cell {

    /** Told of changes to this cell. */
    private final List<CellListener> listeners = new CopyOnWriteArrayList<>();
    final private UUID id = UUID.randomUUID();
    private long onset = 0L;
    private long offset = 0L;
    private Argument type;
    private boolean selected;
    private boolean highlighted;
    private Variable parent;
    private Map<String, CellValue> arguments = new HashMap<String, CellValue>();
    private CellValue cellValue;
    /** What a media column's cell plays; null in an annotation column. */
    private MediaClip clip;

    /** Whether the clip's file is there but could not be opened; not saved. */
    private boolean clipFailed;

    public DatavyuCell() {
    }

    public DatavyuCell(Variable parent, Argument type) {
        this.parent = parent;
        this.type = type;

        this.onset = 0L;
        this.offset = 0L;
        // A new cell is selected and highlighted, unless its column is hidden.
        this.selected = shown();
        this.highlighted = selected;

        // One value for each of the column's codes
        this.cellValue = new DatavyuMatrixCellValue(getID(), type, this);
    }

    /** A media column's cell, which plays a clip from its onset for the clip's duration. */
    DatavyuCell(Variable parent, Argument type, long onset, MediaClip clip) {
        this(parent, type);
        this.clip = clip;
        this.onset = Timestamp.requireNotNegative(onset);
        this.offset = onset + clip.duration();
    }


    @Override

    public Variable getVariable() {
        return parent;
    }

    @Override
    public String getOffsetString() {
        return Timestamp.format(offset);
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public void setOffset(final long newOffset) {
        // A clip's offset is its onset plus its duration, never set on its own.
        if (clip != null || parent.refusesEdits()) {
            return;
        }
        Timestamp.requireNotNegative(newOffset);
        if (newOffset != offset) parent.getOwningDatastore().markAsChanged();
        offset = newOffset;
        for (CellListener cl : listeners) {
            cl.offsetChanged(offset);
        }
    }

    @Override
    public void setOffset(final String newOffset) {
        setOffset(Timestamp.parse(newOffset));
    }

    @Override
    public Cell getFreshCell() {
        return this;
    }

    @Override
    public long getOnset() {
        return onset;
    }

    @Override
    public void setOnset(final String newOnset) {
        setOnset(Timestamp.parse(newOnset));
    }

    @Override
    public void setOnset(final long newOnset) {
        if (parent.refusesEdits()) {
            return;
        }
        Timestamp.requireNotNegative(newOnset);
        if (newOnset != onset) parent.getOwningDatastore().markAsChanged();
        onset = newOnset;
        for (CellListener cl : listeners) {
            cl.onsetChanged(onset);
        }
        if (clip != null) {
            clipOffsetChanged();
        }
    }

    @Override
    public MediaClip getClip() {
        return clip;
    }

    @Override
    public void setClip(final MediaClip newClip) {
        if (clip == null) {
            throw new IllegalStateException("An annotation cell has no clip");
        }
        if (!newClip.equals(clip)) {
            parent.getOwningDatastore().markAsChanged();
        }
        clip = newClip;
        for (CellListener cl : listeners) {
            cl.valueChange(cellValue);
        }
        clipOffsetChanged();
    }

    @Override
    public boolean isClipFailed() {
        return clipFailed;
    }

    @Override
    public void setClipFailed(final boolean failed) {
        if (clip == null) {
            throw new IllegalStateException("An annotation cell has no clip");
        }
        if (failed != clipFailed) {
            clipFailed = failed;
            for (CellListener cl : listeners) {
                cl.valueChange(cellValue);
            }
        }
    }

    private void clipOffsetChanged() {
        long newOffset = onset + clip.duration();
        if (newOffset != offset) {
            offset = newOffset;
            for (CellListener cl : listeners) {
                cl.offsetChanged(offset);
            }
        }
    }

    @Override
    public String getOnsetString() {
        return Timestamp.format(onset);

    }

    @Override
    public String getValueAsString() {
        return getCellValue().toString();
    }

    @Override
    public CellValue getCellValue() {
        return this.cellValue;
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    /** @return whether the cell's column is shown, so the cell can be selected */
    private boolean shown() {
        return parent == null || !parent.isHidden();
    }

    @Override
    public void setSelected(final boolean select) {
        boolean now = select && shown();
        this.selected = now;
        if (!now) {
            setHighlighted(false);
        }

        for (CellListener cl : listeners) {
            cl.selectionChange(now);
            if (!now) {
                cl.highlightingChange(false);
            }
        }
    }

    @Override
    public boolean isHighlighted() {
        return highlighted;
    }

    @Override
    public void setHighlighted(final boolean highlight) {
        // A highlighted cell is selected, so a cell in a hidden column is neither.
        boolean now = highlight && shown();
        this.highlighted = now;

        if (now) {
            setSelected(true);
        }

        for (CellListener cl : listeners) {
            cl.highlightingChange(now);
        }
    }

    @Override
    public void addMatrixValue(Argument type) {
        DatavyuMatrixCellValue val = (DatavyuMatrixCellValue) getCellValue();
        val.createArgument(type);
    }

    @Override
    public void moveMatrixValue(final int oldIndex, int newIndex) {
        DatavyuMatrixCellValue val = (DatavyuMatrixCellValue) getCellValue();
        List<CellValue> cellValues = val.getArguments();
        CellValue v = cellValues.get(oldIndex);

        cellValues.remove(oldIndex);
        cellValues.add(newIndex, v);

        for (int i = 0; i < cellValues.size(); i++) {
            ((DatavyuCellValue) cellValues.get(i)).setIndex(i);
        }
    }

    @Override
    public void removeMatrixValue(final int index) {
        ((DatavyuMatrixCellValue) getCellValue()).removeArgument(index);
    }

    @Override
    public void setMatrixValue(final int index, final String value) {
        DatavyuMatrixCellValue val = (DatavyuMatrixCellValue) getCellValue();
        List<CellValue> cellValues = val.getArguments();
        CellValue code = cellValues.get(index);
        String before = code.toString();
        code.set(value);
        if (!code.toString().equals(before)) {
            for (CellListener cl : listeners) {
                cl.valueChange(val);
            }
        }
    }

    @Override
    public CellValue getMatrixValue(final int index) {
        return ((DatavyuMatrixCellValue) getCellValue()).getArguments().get(index);
    }

    @Override
    public void addListener(final CellListener cellListener) {
        listeners.add(cellListener);
    }

    @Override
    public void removeListener(final CellListener cellListener) {
        listeners.remove(cellListener);
    }

    public UUID getID() {
        return id;
    }

    @Override
    public int hashCode() {
        return this.getID().hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DatavyuCell)) {
            return false;
        }
        return id.equals(((DatavyuCell) other).id);
    }

    @Override
    public boolean isInTimeWindow(long time) {
        if (time >= getOnset() && time <= getOffset()) {
            return true;
        } else {
            return false;
        }
    }
    
    @Override
    /* Print string representation of this cell. */
    public String toString(){
        return "[" +onset+ "," + offset + "," + getValueAsString()+ "]";
    }
}


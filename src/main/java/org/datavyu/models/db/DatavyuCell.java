/*
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
    private long onset;
    private long offset;
    private boolean selected;
    private boolean highlighted;
    private Variable parent;
    /** One value for each of the column's codes, in their order; {@code ""} is empty. */
    private final List<String> values = new ArrayList<>();
    /** What a media column's cell plays; null in an annotation column. */
    private MediaClip clip;

    /** What the player last found of the clip's file; not saved. */
    private ClipFile clipFile = ClipFile.FOUND;

    /**
     * An annotation column's cell, with an empty value for each of its codes.
     *
     * @param parent the column
     * @param codes  how many codes it has
     */
    DatavyuCell(final Variable parent, final int codes) {
        this.parent = parent;

        this.onset = 0L;
        this.offset = 0L;
        // A new cell is selected and highlighted, unless its column is hidden.
        this.selected = shown();
        this.highlighted = selected;

        // One value for each of the column's codes
        for (int i = 0; i < codes; i++) {
            values.add("");
        }
    }

    /** A media column's cell, which plays a clip from its onset for the clip's duration. */
    DatavyuCell(final Variable parent, final long onset, final MediaClip clip) {
        this(parent, 0);
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
            cl.contentChanged();
        }
        clipOffsetChanged();
    }

    @Override
    public ClipFile getClipFile() {
        return clipFile;
    }

    @Override
    public void setClipFile(final ClipFile found) {
        if (clip == null) {
            throw new IllegalStateException("An annotation cell has no clip");
        }
        if (found != clipFile) {
            clipFile = Objects.requireNonNull(found);
            for (CellListener cl : listeners) {
                cl.contentChanged();
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
    public List<String> getValues() {
        return List.copyOf(values);
    }

    @Override
    public String getValue(final int code) {
        return values.get(code);
    }

    @Override
    public void setValue(final int code, final String value) {
        Objects.requireNonNull(value, "an empty value is \"\"");
        // Read first, so a code the column does not have is refused even when it is locked.
        String before = values.get(code);
        if (parent.refusesEdits() || before.equals(value)) {
            return;
        }
        values.set(code, value);
        valuesChanged();
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
    public void setValues(final List<String> newValues) {
        if (newValues.size() != values.size()) {
            throw new IllegalArgumentException("Cell has " + values.size() + " values, not " + newValues.size());
        }
        List<String> copy = List.copyOf(newValues);
        if (parent.refusesEdits() || copy.equals(values)) {
            return;
        }
        values.clear();
        values.addAll(copy);
        valuesChanged();
    }

    private void valuesChanged() {
        parent.getOwningDatastore().markAsChanged();
        for (CellListener cl : listeners) {
            cl.contentChanged();
        }
    }

    /** Gives the cell an empty value for a code its column has inserted at a place. */
    void insertValue(final int code) {
        values.add(code, "");
    }

    /** Moves the cell's value for a code its column has moved. */
    void moveValue(final int from, final int to) {
        values.add(to, values.remove(from));
    }

    /** Drops the cell's value for a code its column has deleted. */
    void removeValue(final int code) {
        values.remove(code);
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
        return id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DatavyuCell cell && id.equals(cell.id);
    }

    @Override
    public boolean isInTimeWindow(long time) {
        return time >= getOnset() && time <= getOffset();
    }
    
    @Override
    /* Print string representation of this cell. */
    public String toString(){
        return "[" + onset + "," + offset + ",(" + String.join(",", values) + ")]";
    }
}


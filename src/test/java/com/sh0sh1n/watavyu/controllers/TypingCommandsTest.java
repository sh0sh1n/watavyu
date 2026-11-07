package com.sh0sh1n.watavyu.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.CellListener;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.MediaClip;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.ColumnListener;
import com.sh0sh1n.watavyu.models.Project;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Typing into a cell is a command: it leaves locked columns and clips alone, changes the cell and records what it did,
 * a value's typing as one edit while it continues and a time's each change as one, so undo puts it back.
 */
public class TypingCommandsTest {

    private static final MediaClip CAM = new MediaClip("/study/cam.mp4", 10_000, 30, 1280, 720, 1);

    private DataStore dataStore;
    private ProjectController spreadsheet;
    private TypingCommands typing;
    private Column gaze;
    private Column speech;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        spreadsheet = new ProjectController(new Project(), dataStore);
        typing = spreadsheet.typing();
        gaze = dataStore.createColumn("gaze");
        speech = dataStore.createColumn("speech");
        dataStore.deselectAll();
    }

    private void undo() {
        spreadsheet.undo();
    }

    private List<String> values(final Cell cell) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < cell.getColumn().getCodes().size(); i++) {
            values.add(cell.getValue(i));
        }
        return values;
    }

    /** Typing a key at a time while the cell keeps the focus is one edit: undo puts back the value before it. */
    @Test
    public void typingIntoACellIsOneEditUntilItLosesTheFocus() {
        Cell cell = gaze.createPointCell(1_000);

        assertTrue(typing.typeValue(cell, 0, "s", false));
        assertTrue(typing.typeValue(cell, 0, "sm", true));
        assertTrue(typing.typeValue(cell, 0, "smile", true));
        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Change Value in gaze");

        undo();
        assertEquals(cell.getValue(0), "");
        assertFalse(spreadsheet.getUndoManager().canUndo());
        spreadsheet.redo();
        assertEquals(cell.getValue(0), "smile");
    }

    @Test
    public void typingThatStartsAgainIsAnEditOfItsOwn() {
        Cell cell = gaze.createPointCell(1_000);
        typing.typeValue(cell, 0, "smile", false);

        typing.typeValue(cell, 0, "frown", false);

        undo();
        assertEquals(cell.getValue(0), "smile");
    }

    /** Another edit between keys ends the typing, so undo takes back the keys after it first. */
    @Test
    public void anotherEditEndsTheTyping() {
        Cell cell = gaze.createPointCell(1_000);
        typing.typeValue(cell, 0, "sm", false);
        cell.setSelected(true);
        spreadsheet.cells().setOnsets(2_000);

        typing.typeValue(cell, 0, "smile", true);

        undo();
        assertEquals(cell.getValue(0), "sm");
        assertEquals(cell.getOnset(), 2_000);
    }

    /** An edit undone while typing still ends it, so the keys after it are an edit of their own. */
    @Test
    public void anEditUndoneWhileTypingStillEndsIt() {
        Cell cell = gaze.createPointCell(1_000);
        typing.typeValue(cell, 0, "sm", false);
        cell.setSelected(true);
        spreadsheet.cells().setOnsets(2_000);
        undo();

        typing.typeValue(cell, 0, "smile", true);

        undo();
        assertEquals(cell.getValue(0), "sm");
    }

    @Test
    public void typingIntoAnotherCellIsAnEditOfItsOwn() {
        Cell cell = gaze.createPointCell(1_000);
        Cell other = gaze.createPointCell(2_000);
        other.setValue(0, "smile");
        typing.typeValue(cell, 0, "smile", false);

        typing.typeValue(other, 0, "frown", true);

        undo();
        assertEquals(other.getValue(0), "smile");
        assertEquals(cell.getValue(0), "smile");
    }

    /** Typing joins only typing that ended where it starts, not a value changed in between by something else. */
    @Test
    public void typingJoinsOnlyFromWhereItLeftOff() {
        Cell cell = gaze.createPointCell(1_000);
        typing.typeValue(cell, 0, "sm", false);
        cell.setValue(0, "grin");

        typing.typeValue(cell, 0, "grins", true);

        undo();
        assertEquals(cell.getValue(0), "grin");
        undo();
        assertEquals(cell.getValue(0), "");
    }

    /** Typing into each code of a cell is one edit, which puts back every code's value, commas and all. */
    @Test
    public void undoPutsBackEveryCodeTypedIntoWithItsCommas() {
        gaze.addCode();
        Cell cell = gaze.createPointCell(1_000);
        cell.setValue(0, "a, b");

        typing.typeValue(cell, 0, "c", false);
        typing.typeValue(cell, 1, "d, e", true);
        assertEquals(values(cell), List.of("c", "d, e"));

        undo();
        assertEquals(values(cell), List.of("a, b", ""));
        spreadsheet.redo();
        assertEquals(values(cell), List.of("c", "d, e"));
    }

    /** Typing, undo and redo all set the values through the cell, so its views hear of each. */
    @Test
    public void typingAndItsUndoAreHeard() {
        Cell cell = gaze.createPointCell(1_000);
        List<String> heard = new ArrayList<>();
        cell.addListener(new CellListener() {
            @Override
            public void offsetChanged(final long newOffset) { }

            @Override
            public void onsetChanged(final long newOnset) { }

            @Override
            public void highlightingChange(final boolean isHighlighted) { }

            @Override
            public void selectionChange(final boolean isSelected) { }

            @Override
            public void contentChanged() {
                heard.add(cell.getValue(0));
            }
        });

        typing.typeValue(cell, 0, "smile", false);
        undo();

        assertEquals(heard, List.of("smile", ""));
    }

    @Test
    public void aCellDeletedOrGoneWithItsColumnIsNotTypedInto() {
        Cell deleted = gaze.createPointCell(1_000);
        Cell gone = speech.createPointCell(1_000);
        spreadsheet.removeCells(List.of(deleted));
        dataStore.removeColumn(speech);

        for (Cell cell : List.of(deleted, gone)) {
            assertFalse(typing.typeValue(cell, 0, "x", false));
            assertFalse(typing.typeOnset(cell, 2_000));
            assertFalse(typing.typeOffset(cell, 2_000));
            assertEquals(cell.getOnset(), 1_000);
            assertEquals(cell.getOffset(), 1_000);
            assertEquals(cell.getValue(0), "");
        }
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    /** The edit is named for its column as it is named now, after any rename since. */
    @Test
    public void anEditIsNamedForItsColumnAsItIsNamedNow() throws Exception {
        Cell cell = gaze.createPointCell(1_000);
        typing.typeValue(cell, 0, "smile", false);
        gaze.setName("look");

        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Change Value in look");
    }

    @Test
    public void typingBackToTheValueBeforeLeavesNothingToUndo() {
        Cell cell = gaze.createPointCell(1_000);
        typing.typeValue(cell, 0, "s", false);
        typing.typeValue(cell, 0, "", true);

        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    @Test
    public void typingThatChangesNothingOrIsRefusedIsNoEdit() throws Exception {
        Cell cell = gaze.createPointCell(1_000);
        cell.setValue(0, "smile");
        Cell clip = dataStore.createMediaColumn("media_1").createClip(0, CAM);
        ColumnListener lock = mock(ColumnListener.class);
        speech.addListener(lock);
        Cell locked = speech.createPointCell(1_000);
        speech.setLocked(true);

        assertFalse(typing.typeValue(cell, 0, "smile", false));
        assertFalse(typing.typeValue(cell, 1, "x", false), "no such code");
        assertFalse(typing.typeValue(cell, -1, "x", false), "no such code");
        assertFalse(typing.typeValue(clip, 0, "x", false));
        assertFalse(typing.typeValue(locked, 0, "x", false));
        verify(lock).editRefused();
        assertEquals(locked.getValue(0), "");
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    /** Each change of a typed time is an edit of its own, and a time already the cell's is none. */
    @Test
    public void eachTypedTimeIsAnEditAndTheSameTimeIsNone() {
        Cell cell = gaze.createPointCell(1_000);

        assertTrue(typing.typeOnset(cell, 2_000));
        assertTrue(typing.typeOffset(cell, 3_000));
        assertFalse(typing.typeOffset(cell, 3_000));

        undo();
        assertEquals(cell.getOffset(), 1_000);
        undo();
        assertEquals(cell.getOnset(), 1_000);
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    @Test
    public void aTypedTimeIsRefusedInALockedColumnAndAsAClipsOffset() throws Exception {
        Cell clip = dataStore.createMediaColumn("media_1").createClip(0, CAM);
        Cell cell = gaze.createPointCell(1_000);
        gaze.setLocked(true);

        assertFalse(typing.typeOnset(cell, 2_000));
        assertFalse(typing.typeOffset(clip, 2_000));
        assertTrue(typing.typeOnset(clip, 2_000), "a clip's onset moves it");
        assertEquals(clip.getOffset(), 12_000);
    }
}

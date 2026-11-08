package com.sh0sh1n.watavyu.views.spreadsheet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.MediaClip;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.ColumnListener;
import com.sh0sh1n.watavyu.models.Project;
import org.testng.annotations.Test;

import javax.swing.JCheckBoxMenuItem;
import java.util.ArrayList;
import java.util.List;

/** A column's header menu locks and unlocks it, as edits in the undo history. */
public class LockColumnTest {

    private static final MediaClip CAM = new MediaClip("/study/cam.mp4", 10_000, 30, 1280, 720, 1);

    @Test
    public void lockColumnTogglesTheColumnsLock() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        for (Column column : List.of(dataStore.createMediaColumn("media_1"), dataStore.createColumn("notes"))) {
            ProjectController spreadsheet = new ProjectController(new Project(), dataStore);

            JCheckBoxMenuItem item = SpreadsheetColumn.lockItem(spreadsheet.columns(), column);
            assertEquals(item.getText(), "Lock Column");
            assertFalse(item.isSelected());

            item.doClick();
            assertTrue(column.isLocked());

            JCheckBoxMenuItem again = SpreadsheetColumn.lockItem(spreadsheet.columns(), column);
            assertTrue(again.isSelected());
            again.doClick();
            assertFalse(column.isLocked());
        }
    }

    /** Undoing past a lock unlocks an annotation column, so a value typed before the lock can be changed back. */
    @Test
    public void undoingALockLetsAnAnnotationColumnChangeAgain() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column notes = dataStore.createColumn("notes");
        Cell cell = notes.createPointCell(1_000);
        ProjectController spreadsheet = new ProjectController(new Project(), dataStore);
        SpreadsheetColumn.lockItem(spreadsheet.columns(), notes).doClick();

        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Lock Column notes");
        cell.setOnset(2_000);
        assertEquals(cell.getOnset(), 1_000L);

        spreadsheet.undo();
        cell.setOnset(2_000);
        assertEquals(cell.getOnset(), 2_000L);
        assertEquals(spreadsheet.getUndoManager().getRedoPresentationName(), "Redo Lock Column notes");
    }

    /** Undoing past a lock unlocks the column, so a clip moved before the lock can be moved back. */
    @Test
    public void undoingALockUnlocksTheColumnAndRedoLocksItAgain() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column media = dataStore.createMediaColumn("media_1");
        Cell clip = media.createClip(2_000, CAM);
        ProjectController spreadsheet = new ProjectController(new Project(), dataStore);
        SpreadsheetColumn.lockItem(spreadsheet.columns(), media).doClick();

        assertEquals(spreadsheet.getUndoManager().getUndoPresentationName(), "Undo Lock Column media_1");
        spreadsheet.undo();
        assertFalse(media.isLocked());
        clip.setOnset(0);
        assertEquals(clip.getOnset(), 0L);

        spreadsheet.redo();
        assertTrue(media.isLocked());
        clip.setOnset(2_000);
        assertEquals(clip.getOnset(), 0L);
    }

    /** The column's header hears of every lock change, from the menu or from undo, to show or hide its lock. */
    @Test
    public void listenersHearOfLockChanges() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column media = dataStore.createMediaColumn("media_1");
        ProjectController spreadsheet = new ProjectController(new Project(), dataStore);
        List<Boolean> heard = new ArrayList<>();
        ColumnListener listener = new ColumnListener() {
            @Override public void nameChanged(final String newName) { }
            @Override public void visibilityChanged(final boolean isHidden) { }
            @Override public void cellInserted(final Cell newCell) { }
            @Override public void cellRemoved(final Cell deletedCell) { }
            @Override public void lockChanged(final boolean locked) { heard.add(locked); }
        };
        media.addListener(listener);
        try {
            SpreadsheetColumn.lockItem(spreadsheet.columns(), media).doClick();
            spreadsheet.undo();
            assertEquals(heard, List.of(true, false));
        } finally {
            media.removeListener(listener);
        }
    }

    /** Renaming a column, which is not an edit, does not stop its lock being undone. */
    @Test
    public void aRenamedColumnsLockCanBeUndone() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column notes = dataStore.createColumn("notes");
        ProjectController spreadsheet = new ProjectController(new Project(), dataStore);
        spreadsheet.columns().setLocked(notes, true);
        notes.setLocked(false);
        notes.setName("looks");
        notes.setLocked(true);

        spreadsheet.undo();

        assertFalse(notes.isLocked());
    }
}

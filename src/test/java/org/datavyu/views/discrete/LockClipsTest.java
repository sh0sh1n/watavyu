package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.MediaClip;
import org.datavyu.models.db.Variable;
import org.datavyu.models.db.VariableListener;
import org.testng.annotations.Test;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.util.ArrayList;
import java.util.List;

/** A media column's header menu locks and unlocks its clips, as edits in the undo history. */
public class LockClipsTest {

    private static final MediaClip CAM = new MediaClip("/study/cam.mp4", 10_000, 30, 1280, 720, 1);

    @Test
    public void lockClipsTogglesTheColumnsLock() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable media = dataStore.createMediaVariable("media_1");
        UndoManager history = new UndoManager();

        JCheckBoxMenuItem item = SpreadsheetColumn.lockItem(dataStore, media, history::addEdit);
        assertEquals(item.getText(), "Lock Clips");
        assertFalse(item.isSelected());

        item.doClick();
        assertTrue(media.isLocked());

        JCheckBoxMenuItem again = SpreadsheetColumn.lockItem(dataStore, media, history::addEdit);
        assertTrue(again.isSelected());
        again.doClick();
        assertFalse(media.isLocked());
    }

    /** Undoing past a lock unlocks the column, so a clip moved before the lock can be moved back. */
    @Test
    public void undoingALockUnlocksTheColumnAndRedoLocksItAgain() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable media = dataStore.createMediaVariable("media_1");
        Cell clip = media.createClip(2_000, CAM);
        UndoManager history = new UndoManager();
        SpreadsheetColumn.lockItem(dataStore, media, history::addEdit).doClick();

        assertEquals(history.getUndoPresentationName(), "Undo Lock Clips of media_1");
        history.undo();
        assertFalse(media.isLocked());
        clip.setOnset(0);
        assertEquals(clip.getOnset(), 0L);

        history.redo();
        assertTrue(media.isLocked());
        clip.setOnset(2_000);
        assertEquals(clip.getOnset(), 0L);
    }

    /** The column's header hears of every lock change, from the menu or from undo, to show or hide its lock. */
    @Test
    public void listenersHearOfLockChanges() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable media = dataStore.createMediaVariable("media_1");
        UndoManager history = new UndoManager();
        List<Boolean> heard = new ArrayList<>();
        VariableListener listener = new VariableListener() {
            @Override public void nameChanged(final String newName) { }
            @Override public void visibilityChanged(final boolean isHidden) { }
            @Override public void cellInserted(final Cell newCell) { }
            @Override public void cellRemoved(final Cell deletedCell) { }
            @Override public void lockChanged(final boolean locked) { heard.add(locked); }
        };
        media.addListener(listener);
        try {
            SpreadsheetColumn.lockItem(dataStore, media, history::addEdit).doClick();
            history.undo();
            assertEquals(heard, List.of(true, false));
        } finally {
            media.removeListener(listener);
        }
    }

    /** A lock whose column has gone is refused, and the edit stays as it was rather than half undone. */
    @Test
    public void aLockWhoseColumnHasGoneIsRefusedAndStaysAsItWas() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable media = dataStore.createMediaVariable("media_1");
        List<UndoableEdit> posted = new ArrayList<>();
        SpreadsheetColumn.lockItem(dataStore, media, posted::add).doClick();
        UndoableEdit lock = posted.get(0);
        dataStore.removeVariable(media);

        assertThrows(CannotUndoException.class, lock::undo);
        assertTrue(lock.canUndo(), "still undoable once the column is back");

        Variable restored = dataStore.createMediaVariable("media_1");
        restored.setLocked(true);
        lock.undo();
        assertFalse(restored.isLocked());

        dataStore.removeVariable(restored);
        assertThrows(CannotRedoException.class, lock::redo);
        assertTrue(lock.canRedo());
    }
}

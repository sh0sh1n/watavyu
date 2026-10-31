package org.datavyu.views.discrete.datavalues;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.MediaClip;
import org.datavyu.models.db.Variable;
import org.datavyu.models.db.VariableListener;
import org.datavyu.views.discrete.datavalues.TimeStampDataValueEditor.TimeStampSource;
import org.testng.annotations.Test;

import java.awt.event.KeyEvent;
import java.nio.file.Path;
import javax.swing.JTextField;

/**
 * In the spreadsheet, a clip's offset cannot be typed, as it follows from its onset and length, and neither can the
 * onset of a clip in a locked media column. An annotation cell's times can be typed.
 */
public class ClipTimesTest {

    private static final MediaClip CAM = new MediaClip("/study/cam.mp4", 10_000, 30, 1280, 720, 1);

    /** @return whether a time can be typed; when it cannot, typing a digit leaves the cell as it was */
    private static boolean typingAllowed(final Cell cell, final TimeStampSource time) {
        TimeStampDataValueEditor editor = new TimeStampDataValueEditor(new JTextField(), cell, time);
        if (!editor.isTypingAllowed()) {
            long onset = cell.getOnset();
            long offset = cell.getOffset();
            KeyEvent typed = new KeyEvent(new JTextField(), KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, '5');
            editor.keyTyped(typed);
            assertTrue(typed.isConsumed());
            assertEquals(cell.getOnset(), onset);
            assertEquals(cell.getOffset(), offset);
        }
        return editor.isTypingAllowed();
    }

    @Test
    public void aClipsOnsetCanBeTypedButNotItsOffset() throws Exception {
        Cell clip = DataStoreFactory.newDataStore().createMediaVariable("media_1").createClip(0, CAM);

        assertTrue(typingAllowed(clip, TimeStampSource.Onset));
        assertFalse(typingAllowed(clip, TimeStampSource.Offset));
    }

    @Test
    public void aLockedClipsOnsetCannotBeTyped() throws Exception {
        Variable media = DataStoreFactory.newDataStore().createMediaVariable("media_1");
        Cell clip = media.createClip(0, CAM);
        media.setLocked(true);

        assertFalse(typingAllowed(clip, TimeStampSource.Onset));
    }

    @Test
    public void anAnnotationCellsTimesCanBeTyped() throws Exception {
        Cell cell = DataStoreFactory.newDataStore().createVariable("notes").createCell();

        assertTrue(typingAllowed(cell, TimeStampSource.Onset));
        assertTrue(typingAllowed(cell, TimeStampSource.Offset));
    }

    @Test
    public void aLockedAnnotationCellsTimesCannotBeTyped() throws Exception {
        Variable notes = DataStoreFactory.newDataStore().createVariable("notes");
        Cell cell = notes.createPointCell(1_000);
        notes.setLocked(true);

        assertFalse(typingAllowed(cell, TimeStampSource.Onset));
        assertFalse(typingAllowed(cell, TimeStampSource.Offset));
    }

    /** Typing a time in a locked column tells the column, so its padlock shakes; a clip's offset does not. */
    @Test
    public void typingInALockedColumnIsHeardAsRefused() throws Exception {
        Variable media = DataStoreFactory.newDataStore().createMediaVariable("media_1");
        Cell clip = media.createClip(0, CAM);
        int[] refusals = {0};
        VariableListener listener = new VariableListener() {
            @Override public void nameChanged(final String newName) { }
            @Override public void visibilityChanged(final boolean isHidden) { }
            @Override public void cellInserted(final Cell newCell) { }
            @Override public void cellRemoved(final Cell deletedCell) { }
            @Override public void editRefused() { refusals[0]++; }
        };
        media.addListener(listener);
        try {
            typingAllowed(clip, TimeStampSource.Offset);
            assertEquals(refusals[0], 0, "an unlocked clip's offset just cannot be typed");

            media.setLocked(true);
            typingAllowed(clip, TimeStampSource.Onset);
            assertEquals(refusals[0], 1);
        } finally {
            media.removeListener(listener);
        }
    }

    /** A clip cell names its file, its frame rate once known for video, and whether the file is missing. */
    @Test
    public void aClipShowsItsFileFrameRateAndWhetherItIsMissing() {
        String present = System.getProperty("java.home");
        String name = Path.of(present).getFileName().toString();

        assertEquals(DataValueEditorFactory.clipLabel(CAM), "cam.mp4 \u00b7 30 fps (missing)");
        assertEquals(DataValueEditorFactory.clipLabel(MediaClip.of(present)), name);
        assertEquals(DataValueEditorFactory.clipLabel(MediaClip.of(present).withDetails(60_000, 29.97, 1920, 1080)),
            name + " \u00b7 29.97 fps");
        assertEquals(DataValueEditorFactory.clipLabel(MediaClip.of(present).withDetails(60_000, 23.976023976, 1920,
            1080)), name + " \u00b7 23.976 fps");
        // Audio has no frame rate.
        assertEquals(DataValueEditorFactory.clipLabel(MediaClip.of(present).withDetails(60_000, 0, 0, 0)), name);
    }
}

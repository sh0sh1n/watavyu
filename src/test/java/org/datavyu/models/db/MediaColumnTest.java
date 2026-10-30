package org.datavyu.models.db;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import java.util.List;

/**
 * A media column's cells are clips: a clip's offset is its onset plus its duration and is never set on its own, and a
 * locked column's clips do not move.
 */
public class MediaColumnTest {

    private static final MediaClip CAM = new MediaClip("/study/cam.mp4", 10_000, 30, 1280, 720, 0.5);

    @Test
    public void aClipEndsItsDurationAfterItsOnset() throws Exception {
        Variable media = DataStoreFactory.newDataStore().createMediaVariable("media_1");

        Cell clip = media.createClip(2_000, CAM);

        assertTrue(media.isMedia());
        assertEquals(clip.getOnset(), 2_000L);
        assertEquals(clip.getOffset(), 12_000L);
        assertEquals(clip.getClip(), CAM);
        assertEquals(media.getCells(), List.of(clip));
        assertEquals(media.getRootNode().childArguments, List.of());
    }

    @Test
    public void movingAClipMovesItsOffset() throws Exception {
        Cell clip = DataStoreFactory.newDataStore().createMediaVariable("media_1").createClip(2_000, CAM);

        clip.setOnset(500);

        assertEquals(clip.getOnset(), 500L);
        assertEquals(clip.getOffset(), 10_500L);
    }

    /** B13: no time is negative, so no clip starts before time zero. */
    @Test
    public void aClipCannotStartBeforeTimeZero() throws Exception {
        Variable media = DataStoreFactory.newDataStore().createMediaVariable("media_1");
        Cell clip = media.createClip(2_000, CAM);

        assertThrows(IllegalArgumentException.class, () -> clip.setOnset(-500));
        assertEquals(clip.getOnset(), 2_000L);
        assertThrows(IllegalArgumentException.class, () -> media.createClip(-1, CAM));
        assertEquals(media.getCells(), List.of(clip));
    }

    @Test
    public void aClipsOffsetIsNotSetOnItsOwn() throws Exception {
        Cell clip = DataStoreFactory.newDataStore().createMediaVariable("media_1").createClip(2_000, CAM);

        clip.setOffset(3_000);

        assertEquals(clip.getOffset(), 12_000L);
    }

    /** A clip whose file has not opened has no length yet; it gets one when the file says how long it is. */
    @Test
    public void aClipsOffsetFollowsWhatItsFileSays() throws Exception {
        Cell clip = DataStoreFactory.newDataStore().createMediaVariable("media_1")
            .createClip(2_000, MediaClip.of("/study/cam.mp4"));
        assertEquals(clip.getOffset(), 2_000L);

        clip.setClip(clip.getClip().withDetails(4_000, 25, 640, 480));

        assertEquals(clip.getOffset(), 6_000L);
        assertEquals(clip.getClip(), new MediaClip("/study/cam.mp4", 4_000, 25, 640, 480, MediaClip.DEFAULT_VOLUME));
    }

    @Test
    public void aLockedColumnsClipsDoNotMove() throws Exception {
        Variable media = DataStoreFactory.newDataStore().createMediaVariable("media_1");
        Cell clip = media.createClip(2_000, CAM);

        media.setLocked(true);
        clip.setOnset(5_000);
        assertEquals(clip.getOnset(), 2_000L);

        media.setLocked(false);
        clip.setOnset(5_000);
        assertEquals(clip.getOnset(), 5_000L);
    }

    @Test
    public void mediaAndAnnotationColumnsKeepTheirOwnKindOfCell() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable media = dataStore.createMediaVariable("media_1");
        Variable notes = dataStore.createVariable("notes");

        assertThrows(IllegalStateException.class, media::createCell);
        assertThrows(IllegalStateException.class, () -> media.createPointCell(0));
        assertThrows(IllegalStateException.class, () -> notes.createClip(0, CAM));
        assertThrows(IllegalStateException.class, () -> notes.createCell().setClip(CAM));
        assertNull(notes.createCell().getClip());
        assertFalse(notes.isMedia());
    }

    @Test
    public void anAnnotationColumnIsNeverLocked() throws Exception {
        Variable notes = DataStoreFactory.newDataStore().createVariable("notes");

        notes.setLocked(true);

        assertFalse(notes.isLocked());
    }

    @Test
    public void mediaNamesShareTheSpreadsheetsNames() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createVariable("media_1");

        assertThrows(UserWarningException.class, () -> dataStore.createMediaVariable("media_1"));
    }
}

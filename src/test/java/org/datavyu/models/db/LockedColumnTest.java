package org.datavyu.models.db;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * A locked column, annotation or media, refuses every change to its cells, codes and name, and its listeners hear of
 * each refusal. Hiding, selecting, reordering and unlocking it are not changes to it.
 */
public class LockedColumnTest {

    private static final MediaClip CAM = new MediaClip("/study/cam.mp4", 10_000, 30, 1280, 720, 1);

    private DataStore dataStore;
    private Variable notes;
    private Cell cell;
    private int refusals;
    private final List<Boolean> lockChanges = new ArrayList<>();
    private final VariableListener listener = new VariableListener() {
        @Override public void nameChanged(final String newName) { }
        @Override public void visibilityChanged(final boolean isHidden) { }
        @Override public void cellInserted(final Cell newCell) { }
        @Override public void cellRemoved(final Cell deletedCell) { }
        @Override public void lockChanged(final boolean locked) { lockChanges.add(locked); }
        @Override public void editRefused() { refusals++; }
    };

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        notes = dataStore.createVariable("notes");
        cell = notes.createPointCell(1_000);
        cell.setMatrixValue(0, "smile");
        notes.addListener(listener);
        refusals = 0;
        lockChanges.clear();
        notes.setLocked(true);
    }

    @AfterMethod
    public void tearDown() {
        notes.removeListener(listener);
    }

    @Test
    public void anAnnotationColumnLocksAndUnlocks() {
        assertTrue(notes.isLocked());
        notes.setLocked(false);
        assertFalse(notes.isLocked());
        assertEquals(lockChanges, List.of(true, false));
        assertEquals(refusals, 0);
    }

    @Test
    public void aLockedColumnsCellsKeepTheirTimesAndValues() {
        cell.setOnset(5_000);
        cell.setOffset(6_000);
        cell.setMatrixValue(0, "frown");
        cell.getMatrixValue(0).clear();

        assertEquals(cell.getOnset(), 1_000L);
        assertEquals(cell.getOffset(), 1_000L);
        assertEquals(cell.getMatrixValue(0).toString(), "smile");
        assertEquals(refusals, 4);
    }

    @Test
    public void aLockedColumnRefusesNewCellsAndKeepsItsCells() {
        assertThrows(IllegalStateException.class, notes::createCell);
        assertThrows(IllegalStateException.class, () -> notes.createPointCell(2_000));
        notes.removeCell(cell);
        dataStore.removeCell(cell);

        assertEquals(notes.getCells(), List.of(cell));
        assertEquals(refusals, 4);
    }

    @Test
    public void aLockedColumnKeepsItsCodesAndName() {
        List<Argument> codes = List.copyOf(notes.getRootNode().childArguments);
        String code = codes.get(0).name;
        assertThrows(IllegalStateException.class, notes::addArgument);
        notes.removeArgument(code);
        notes.moveArgument(0, 0);
        assertThrows(UserWarningException.class, () -> notes.setName("renamed"));

        assertEquals(notes.getRootNode().childArguments, codes);
        assertEquals(cell.getMatrixValue(0).toString(), "smile");
        assertEquals(notes.getName(), "notes");
        assertEquals(refusals, 4);
    }

    @Test
    public void hidingSelectingAndReorderingAreNotChanges() {
        notes.setHidden(true);
        notes.setSelected(false);
        notes.setOrderIndex(3);
        cell.setSelected(true);

        assertTrue(notes.isHidden());
        assertEquals(notes.getOrderIndex(), 3);
        assertEquals(refusals, 0);
    }

    @Test
    public void anUnlockedColumnTakesChangesAgain() throws Exception {
        notes.setLocked(false);

        cell.setOnset(5_000);
        cell.setMatrixValue(0, "frown");
        notes.createPointCell(2_000);
        notes.setName("renamed");

        assertEquals(cell.getOnset(), 5_000L);
        assertEquals(cell.getMatrixValue(0).toString(), "frown");
        assertEquals(notes.getCells().size(), 2);
        assertEquals(refusals, 0);
    }

    @Test
    public void askingWhetherAnUnlockedColumnRefusesTellsNoOne() {
        notes.setLocked(false);

        assertFalse(notes.refusesEdits());
        assertEquals(refusals, 0);

        notes.setLocked(true);
        assertTrue(notes.refusesEdits());
        assertEquals(refusals, 1);
    }

    /** A locked media column refuses new clips and keeps its clips, while what the file says of a clip still applies. */
    @Test
    public void aLockedMediaColumnKeepsItsClips() throws Exception {
        Variable media = dataStore.createMediaVariable("media_1");
        Cell clip = media.createClip(2_000, CAM);
        media.setLocked(true);

        assertThrows(IllegalStateException.class, () -> media.createClip(0, CAM));
        clip.setOnset(0);
        media.removeCell(clip);
        clip.setClip(CAM.withDetails(4_000, 25, 640, 480));

        assertEquals(media.getCells(), List.of(clip));
        assertEquals(clip.getOnset(), 2_000L);
        assertEquals(clip.getOffset(), 6_000L);
    }

    @Test
    public void lockingMarksTheProjectChanged() {
        dataStore.markAsUnchanged();
        notes.setLocked(false);
        assertTrue(dataStore.isChanged());
    }
}

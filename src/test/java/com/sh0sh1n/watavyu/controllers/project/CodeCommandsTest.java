package com.sh0sh1n.watavyu.controllers.project;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.UserWarningException;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.ColumnListener;
import com.sh0sh1n.watavyu.models.Project;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * The Code Editor's changes to a column's codes and name are commands, each an edit in the spreadsheet's undo history,
 * and the cells' values follow the codes both ways (B21: they used to be no edit at all, and deleting a code lost its
 * values for good).
 */
public class CodeCommandsTest {

    private DataStore dataStore;
    private ProjectController spreadsheet;
    private CodeCommands codes;
    private Column gaze;
    private Cell first;
    private Cell second;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        spreadsheet = new ProjectController(new Project(), dataStore);
        codes = spreadsheet.codes();
        gaze = dataStore.createColumn("gaze");
        gaze.addCode();
        gaze.addCode();
        first = gaze.createPointCell(1_000);
        second = gaze.createPointCell(2_000);
        first.setValue(0, "left");
        first.setValue(1, "up");
        first.setValue(2, "near");
        second.setValue(0, "right");
        second.setValue(2, "far");
    }

    private List<String> names() {
        return gaze.getCodes();
    }

    private static String values(final Cell cell) {
        return "(" + String.join(",", cell.getValues()) + ")";
    }

    private String undoName() {
        return spreadsheet.getUndoManager().getUndoPresentationName();
    }

    // Add

    @Test
    public void aNewCodeGoesLastWithAnEmptyValueInEachCell() {
        assertEquals(codes.add(gaze), 3);

        assertEquals(names(), List.of("code01", "code02", "code03", "code04"));
        assertEquals(values(first), "(left,up,near,)");
        assertEquals(undoName(), "Undo New Code code04");
    }

    @Test
    public void undoTakesANewCodeOutAndRedoPutsItBack() {
        codes.add(gaze);

        spreadsheet.undo();
        assertEquals(names(), List.of("code01", "code02", "code03"));
        assertEquals(values(first), "(left,up,near)");

        spreadsheet.redo();
        assertEquals(names(), List.of("code01", "code02", "code03", "code04"));
        assertEquals(values(second), "(right,,far,)");
    }

    // Delete

    @Test
    public void undoPutsADeletedCodeBackInItsPlaceWithItsValues() {
        assertTrue(codes.delete(gaze, 1));
        assertEquals(names(), List.of("code01", "code03"));
        assertEquals(values(first), "(left,near)");
        assertEquals(undoName(), "Undo Delete Code code02");

        spreadsheet.undo();
        assertEquals(names(), List.of("code01", "code02", "code03"));
        assertEquals(values(first), "(left,up,near)");
        assertEquals(values(second), "(right,,far)");

        spreadsheet.redo();
        assertEquals(values(second), "(right,far)");
    }

    @Test
    public void aCodeDeletedFromACellUndoneAfterItsDeletionComesBackWithIt() {
        codes.delete(gaze, 2);
        first.setSelected(true);
        spreadsheet.cells().deleteSelected();

        spreadsheet.undo();
        spreadsheet.undo();

        assertEquals(gaze.getCells(), List.of(first, second));
        assertEquals(values(first), "(left,up,near)");
    }

    @Test
    public void theLastCodeCanBeDeletedAndPutBack() throws Exception {
        Column timing = dataStore.createColumn("timing");
        Cell cell = timing.createPointCell(0);
        cell.setValue(0, "only");

        codes.delete(timing, 0);
        assertTrue(timing.getCodes().isEmpty());

        spreadsheet.undo();
        assertEquals(cell.getValue(0), "only");
    }

    // Move

    @Test
    public void movingACodeMovesItsValuesAndUndoMovesThemBack() {
        assertTrue(codes.move(gaze, 0, 2));
        assertEquals(names(), List.of("code02", "code03", "code01"));
        assertEquals(values(first), "(up,near,left)");
        assertEquals(undoName(), "Undo Move Code code01");

        spreadsheet.undo();
        assertEquals(names(), List.of("code01", "code02", "code03"));
        assertEquals(values(first), "(left,up,near)");

        spreadsheet.redo();
        assertEquals(values(second), "(,far,right)");
    }

    @Test
    public void aMovePastEitherEndOrToItsOwnPlaceIsNoEdit() {
        assertFalse(codes.move(gaze, 0, -1));
        assertFalse(codes.move(gaze, 2, 3));
        assertFalse(codes.move(gaze, 1, 1));

        assertEquals(names(), List.of("code01", "code02", "code03"));
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    // Rename a code

    @Test
    public void theKeysTypedIntoACodesNameUndoTogether() {
        codes.rename(gaze, 0, "code01l", false);
        codes.rename(gaze, 0, "code01lo", true);
        codes.rename(gaze, 0, "look", true);
        assertEquals(names().get(0), "look");
        assertEquals(undoName(), "Undo Rename Code code01 to look");

        spreadsheet.undo();
        assertEquals(names().get(0), "code01");
        assertFalse(spreadsheet.getUndoManager().canUndo());

        spreadsheet.redo();
        assertEquals(names().get(0), "look");
    }

    @Test
    public void aRenameThatDoesNotContinueTheLastIsItsOwnEdit() {
        codes.rename(gaze, 0, "look", false);
        codes.rename(gaze, 0, "glance", false);
        codes.rename(gaze, 1, "pitch", true);

        spreadsheet.undo();
        assertEquals(names(), List.of("glance", "code02", "code03"));
        spreadsheet.undo();
        assertEquals(names(), List.of("look", "code02", "code03"));
    }

    @Test
    public void typingAfterAnUndoStartsAFreshEdit() {
        codes.rename(gaze, 0, "look", false);
        codes.add(gaze);
        spreadsheet.undo();
        codes.rename(gaze, 0, "looks", true);

        spreadsheet.undo();
        assertEquals(names().get(0), "look");
    }

    /** A project file holds no empty or repeated code name, so the column keeps the last good one. */
    @Test
    public void anEmptyNameOrAnotherCodesIsRefused() {
        assertFalse(codes.rename(gaze, 0, "", false));
        assertFalse(codes.rename(gaze, 0, "code02", false));

        assertEquals(names(), List.of("code01", "code02", "code03"));
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    /** Typing a name back to what it was leaves nothing to undo, rather than a step that changes nothing. */
    @Test
    public void typingBackToTheOldNameLeavesNothingToUndo() throws Exception {
        codes.rename(gaze, 0, "code01x", false);
        codes.rename(gaze, 0, "code01", true);
        assertFalse(spreadsheet.getUndoManager().canUndo());

        codes.add(gaze);
        spreadsheet.columns().rename(gaze, "gazes", false);
        spreadsheet.columns().rename(gaze, "gaze", true);
        assertEquals(undoName(), "Undo New Code code04");
        spreadsheet.undo();
        assertEquals(names(), List.of("code01", "code02", "code03"));
        assertEquals(gaze.getName(), "gaze");
    }

    /** A place no code has, as a stale selection in the Code Editor can give, is no edit. */
    @Test
    public void aPlaceOutOfRangeIsNoEdit() {
        assertFalse(codes.delete(gaze, 3));
        assertFalse(codes.delete(gaze, -1));
        assertFalse(codes.rename(gaze, 3, "look", false));

        assertEquals(names(), List.of("code01", "code02", "code03"));
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    @Test
    public void theSameNameIsNoEdit() {
        assertFalse(codes.rename(gaze, 0, "code01", false));
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    // Locked

    @Test
    public void aLockedColumnRefusesEachCodeCommandAndShowsIt() {
        ColumnListener listener = mock(ColumnListener.class);
        gaze.addListener(listener);
        gaze.setLocked(true);

        assertEquals(codes.add(gaze), -1);
        assertFalse(codes.delete(gaze, 0));
        assertFalse(codes.move(gaze, 0, 1));
        assertFalse(codes.rename(gaze, 0, "look", false));

        assertEquals(names(), List.of("code01", "code02", "code03"));
        assertEquals(values(first), "(left,up,near)");
        assertFalse(spreadsheet.getUndoManager().canUndo());
        verify(listener, times(4)).editRefused();
        verify(listener, never()).codesChanged();
    }

    /** The spreadsheet and the Code Editor redraw on each change, undo included. */
    @Test
    public void listenersHearOfEachChangeToTheCodes() {
        ColumnListener listener = mock(ColumnListener.class);
        gaze.addListener(listener);

        codes.add(gaze);
        codes.delete(gaze, 0);
        codes.move(gaze, 0, 1);
        codes.rename(gaze, 0, "look", false);
        spreadsheet.undo();

        verify(listener, times(3)).codesChanged();
        verify(listener, times(2)).codeRenamed(0);
    }

    /** A column read from a file holds codes it did not number, and its next code takes a free name. */
    @Test
    public void aNewCodeTakesANameNoCodeHas() {
        gaze.renameCode(2, "code05");
        gaze.insertCode(3, "code04");

        int added = codes.add(gaze);

        assertEquals(gaze.getCodes().get(added), "code06");
    }

    // Rename a column

    @Test
    public void theKeysTypedIntoAColumnsNameUndoTogether() throws Exception {
        ColumnCommands columns = spreadsheet.columns();
        assertTrue(columns.rename(gaze, "gazes", false));
        columns.rename(gaze, "gazest", true);
        assertEquals(undoName(), "Undo Rename Column gaze to gazest");

        spreadsheet.undo();
        assertSame(dataStore.getColumn("gaze"), gaze);
        assertNull(dataStore.getColumn("gazest"));
        assertFalse(spreadsheet.getUndoManager().canUndo());

        spreadsheet.redo();
        assertSame(dataStore.getColumn("gazest"), gaze);
    }

    @Test
    public void aTakenOrMediaOrUnchangedNameIsNoEdit() throws Exception {
        ColumnCommands columns = spreadsheet.columns();
        dataStore.createColumn("speech");
        Column media = dataStore.createMediaColumn("media_1");

        assertThrows(UserWarningException.class, () -> columns.rename(gaze, "speech", false));
        assertThrows(UserWarningException.class, () -> columns.rename(gaze, "1st", false));
        assertThrows(UserWarningException.class, () -> columns.rename(media, "video", false));
        assertFalse(columns.rename(gaze, "gaze", false));

        assertEquals(gaze.getName(), "gaze");
        assertEquals(media.getName(), "media_1");
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    @Test
    public void aLockedColumnKeepsItsName() {
        gaze.setLocked(true);

        assertThrows(UserWarningException.class, () -> spreadsheet.columns().rename(gaze, "look", false));
        assertFalse(spreadsheet.getUndoManager().canUndo());
    }

    /** A name kept from a project file, which a new column could not take, comes back on undo. */
    @Test
    public void undoPutsBackANameKeptFromAProjectFile() throws Exception {
        gaze.setName("Gaze direction", true);

        spreadsheet.columns().rename(gaze, "gaze", false);
        spreadsheet.undo();

        assertEquals(gaze.getName(), "Gaze direction");
    }

    @Test
    public void undoingARenameWhoseOldNameIsTakenIsRefused() throws Exception {
        spreadsheet.columns().rename(gaze, "look", false);
        Column taken = dataStore.createColumn("gaze");

        assertEquals(spreadsheet.undo(), "Another column is named gaze. Rename or delete it to undo this.");
        assertEquals(gaze.getName(), "look");

        dataStore.removeColumn(taken);
        assertNull(spreadsheet.undo());
        assertSame(dataStore.getColumn("gaze"), gaze);
    }
}

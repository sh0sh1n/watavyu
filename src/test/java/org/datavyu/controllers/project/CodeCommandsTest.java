package org.datavyu.controllers.project;

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

import org.datavyu.models.db.Argument;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.datavyu.models.db.VariableListener;
import org.datavyu.models.project.Project;
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
    private Variable gaze;
    private Cell first;
    private Cell second;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        spreadsheet = new ProjectController(new Project(), dataStore);
        codes = spreadsheet.codes();
        gaze = dataStore.createVariable("gaze");
        gaze.addArgument();
        gaze.addArgument();
        first = gaze.createPointCell(1_000);
        second = gaze.createPointCell(2_000);
        first.getCellValue().set("left,up,near");
        second.getCellValue().set("right,,far");
    }

    private List<String> names() {
        return gaze.getRootNode().childArguments.stream().map(code -> code.name).toList();
    }

    private static String values(final Cell cell) {
        return cell.getCellValue().toString();
    }

    private String undoName() {
        return spreadsheet.getUndoManager().getUndoPresentationName();
    }

    // Add

    @Test
    public void aNewCodeGoesLastWithAnEmptyValueInEachCell() {
        Argument code = codes.add(gaze);

        assertEquals(names(), List.of("code01", "code02", "code03", "code04"));
        assertSame(gaze.getRootNode().childArguments.get(3), code);
        assertEquals(values(first), "(left,up,near,)");
        assertEquals(undoName(), "Undo New Code code04");
    }

    @Test
    public void undoTakesANewCodeOutAndRedoPutsTheSameCodeBack() {
        Argument code = codes.add(gaze);

        spreadsheet.undo();
        assertEquals(names(), List.of("code01", "code02", "code03"));
        assertEquals(values(first), "(left,up,near)");

        spreadsheet.redo();
        assertSame(gaze.getRootNode().childArguments.get(3), code);
        assertEquals(values(second), "(right,,far,)");
    }

    // Delete

    @Test
    public void undoPutsADeletedCodeBackInItsPlaceWithItsValues() {
        Argument code = gaze.getRootNode().childArguments.get(1);

        assertTrue(codes.delete(gaze, 1));
        assertEquals(names(), List.of("code01", "code03"));
        assertEquals(values(first), "(left,near)");
        assertEquals(undoName(), "Undo Delete Code code02");

        spreadsheet.undo();
        assertEquals(names(), List.of("code01", "code02", "code03"));
        assertSame(gaze.getRootNode().childArguments.get(1), code);
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
        Variable timing = dataStore.createVariable("timing");
        Cell cell = timing.createPointCell(0);
        cell.setMatrixValue(0, "only");

        codes.delete(timing, 0);
        assertTrue(timing.getRootNode().childArguments.isEmpty());

        spreadsheet.undo();
        assertEquals(cell.getMatrixValue(0).toString(), "only");
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
        VariableListener listener = mock(VariableListener.class);
        gaze.addListener(listener);
        gaze.setLocked(true);

        assertNull(codes.add(gaze));
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
        VariableListener listener = mock(VariableListener.class);
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
        gaze.renameArgument(2, "code05");
        gaze.getRootNode().childArguments.add(new Argument("code04"));

        assertEquals(codes.add(gaze).name, "code06");
    }

    // Rename a column

    @Test
    public void theKeysTypedIntoAColumnsNameUndoTogether() throws Exception {
        ColumnCommands columns = spreadsheet.columns();
        assertTrue(columns.rename(gaze, "gazes", false));
        columns.rename(gaze, "gazest", true);
        assertEquals(undoName(), "Undo Rename Column gaze to gazest");

        spreadsheet.undo();
        assertSame(dataStore.getVariable("gaze"), gaze);
        assertNull(dataStore.getVariable("gazest"));
        assertFalse(spreadsheet.getUndoManager().canUndo());

        spreadsheet.redo();
        assertSame(dataStore.getVariable("gazest"), gaze);
    }

    @Test
    public void aTakenOrMediaOrUnchangedNameIsNoEdit() throws Exception {
        ColumnCommands columns = spreadsheet.columns();
        dataStore.createVariable("speech");
        Variable media = dataStore.createMediaVariable("media_1");

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

    /** A name kept from a Datavyu file, which Watavyu would not allow, comes back on undo. */
    @Test
    public void undoPutsBackANameKeptFromAnImportedFile() throws Exception {
        gaze.setName("Gaze direction", true);

        spreadsheet.columns().rename(gaze, "gaze", false);
        spreadsheet.undo();

        assertEquals(gaze.getName(), "Gaze direction");
    }

    @Test
    public void undoingARenameWhoseOldNameIsTakenIsRefused() throws Exception {
        spreadsheet.columns().rename(gaze, "look", false);
        Variable taken = dataStore.createVariable("gaze");

        assertEquals(spreadsheet.undo(), "Another column is named gaze. Rename or delete it to undo this.");
        assertEquals(gaze.getName(), "look");

        dataStore.removeVariable(taken);
        assertNull(spreadsheet.undo());
        assertSame(dataStore.getVariable("gaze"), gaze);
    }
}

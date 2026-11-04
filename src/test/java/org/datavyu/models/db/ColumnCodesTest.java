package org.datavyu.models.db;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.function.Consumer;

/**
 * An annotation column's codes are names, in order. A new column has one, {@code code01}; reading a file gives a
 * column without cells the codes it was saved with; and each change to them is a change to save.
 */
public class ColumnCodesTest {

    private DataStore dataStore;
    private Variable column;

    @BeforeMethod
    public void setUp() throws UserWarningException {
        dataStore = DataStoreFactory.newDataStore();
        column = dataStore.createVariable("gaze");
    }

    @Test
    public void aNewColumnHasOneCode() throws UserWarningException {
        assertEquals(column.getCodes(), List.of("code01"));
        assertEquals(dataStore.createMediaVariable("media_1").getCodes(), List.of());
    }

    @Test
    public void theCodesGivenOutAreACopy() {
        List<String> codes = column.getCodes();
        column.addCode();

        assertEquals(codes, List.of("code01"));
        assertThrows(UnsupportedOperationException.class, () -> column.getCodes().add("code02"));
    }

    @Test
    public void aColumnWithoutCellsIsGivenItsCodes() {
        column.setCodes(List.of("direction", "distance"));

        assertEquals(column.getCodes(), List.of("direction", "distance"));
        assertEquals(column.createCell().getValues(), List.of("", ""));
    }

    /** The code01 a new column starts with is replaced, so it was never one the column had. */
    @Test
    public void aColumnGivenCodesNumbersItsNextCodeFromCode01() {
        column.setCodes(List.of("direction", "code02"));

        column.addCode();
        column.addCode();

        assertEquals(column.getCodes(), List.of("direction", "code02", "code01", "code03"));
    }

    /** The Code Editor, open as a file is imported, shows the codes the column is given. */
    @Test
    public void listenersHearOfTheCodesGiven() {
        VariableListener listener = mock(VariableListener.class);
        column.addListener(listener);

        column.setCodes(List.of("direction"));

        verify(listener).codesChanged();
    }

    /** A cell has a value for each code, so codes given at once would leave its values out of step. */
    @Test
    public void aColumnWithCellsIsNotGivenCodes() {
        column.createCell();

        assertThrows(IllegalStateException.class, () -> column.setCodes(List.of("direction", "distance")));
        assertEquals(column.getCodes(), List.of("code01"));
    }

    @Test
    public void aMediaOrLockedColumnTakesNoCodes() throws UserWarningException {
        Variable media = dataStore.createMediaVariable("media_1");
        assertThrows(IllegalStateException.class, () -> media.setCodes(List.of("direction")));
        assertEquals(media.getCodes(), List.of());

        assertThrows(IllegalStateException.class, media::addCode);
        assertEquals(media.getCodes(), List.of());

        column.setLocked(true);
        assertThrows(IllegalStateException.class, () -> column.setCodes(List.of("direction")));
        assertEquals(column.getCodes(), List.of("code01"));
    }

    @Test
    public void eachChangeToTheCodesIsAChangeToSave() {
        column.addCode();
        List<Consumer<Variable>> changes = List.of(
            Variable::addCode,
            changed -> changed.renameCode(0, "direction"),
            changed -> changed.moveCode(0, 1),
            changed -> changed.removeCode(0),
            changed -> changed.insertCode(0, "distance"));

        for (Consumer<Variable> change : changes) {
            dataStore.markAsUnchanged();
            change.accept(column);
            assertTrue(dataStore.isChanged());
        }
        dataStore.markAsUnchanged();
        assertFalse(dataStore.isChanged());
    }
}

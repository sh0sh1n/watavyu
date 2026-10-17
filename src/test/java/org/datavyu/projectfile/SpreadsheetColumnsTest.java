package org.datavyu.projectfile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.projectfile.ProjectDocument.AnnotationCell;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.testng.annotations.Test;

import java.util.List;

public class SpreadsheetColumnsTest {

    private static DataStore spreadsheet() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        // The spreadsheet panel numbers columns as it shows them.
        Variable text = dataStore.createVariable("Notes");
        text.getRootNode().childArguments.get(0).name = "note";
        text.setOrderIndex(0);
        Cell later = text.createCell();
        later.setOnset(5_000);
        later.setOffset(6_000);
        later.setMatrixValue(0, "second");
        Cell earlier = text.createCell();
        earlier.setOnset(1_000);
        earlier.setOffset(1_000);
        earlier.setMatrixValue(0, "first, (with) \"quotes\"");

        Variable matrix = dataStore.createVariable("Mother");
        matrix.setOrderIndex(1);
        matrix.addArgument();
        Cell cell = matrix.createCell();
        cell.setOnset(2_000);
        cell.setOffset(3_000);
        cell.setMatrixValue(1, "happy");

        Variable hidden = dataStore.createVariable("Hidden");
        hidden.setOrderIndex(2);
        hidden.setHidden(true);
        return dataStore;
    }

    @Test
    public void columnsAreSavedInSpreadsheetOrderWithCellsInTimeOrder() throws Exception {
        List<AnnotationColumn> columns = SpreadsheetColumns.of(spreadsheet());

        assertEquals(columns, List.of(
            new AnnotationColumn("Notes", List.of("note"), false, List.of(
                new AnnotationCell(1_000, 1_000, List.of("first, (with) \"quotes\"")),
                new AnnotationCell(5_000, 6_000, List.of("second")))),
            new AnnotationColumn("Mother", List.of("code01", "code02"), false, List.of(
                new AnnotationCell(2_000, 3_000, List.of("", "happy")))),
            new AnnotationColumn("Hidden", List.of("code01"), true, List.of())));
    }

    @Test
    public void savedColumnsOpenAsTheSameColumns() throws Exception {
        List<AnnotationColumn> columns = SpreadsheetColumns.of(spreadsheet());
        DataStore reopened = DataStoreFactory.newDataStore();

        SpreadsheetColumns.addTo(reopened, columns);

        assertEquals(SpreadsheetColumns.of(reopened), columns);
    }

    @Test
    public void aColumnWithNoCodesKeepsItsTiming() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable timing = dataStore.createVariable("Timing");
        timing.removeArgument("code01");
        Cell cell = timing.createCell();
        cell.setOnset(10);
        cell.setOffset(20);

        List<AnnotationColumn> columns = SpreadsheetColumns.of(dataStore);
        assertEquals(columns, List.of(
            new AnnotationColumn("Timing", List.of(), false, List.of(new AnnotationCell(10, 20, List.of())))));

        DataStore reopened = DataStoreFactory.newDataStore();
        SpreadsheetColumns.addTo(reopened, columns);
        assertEquals(SpreadsheetColumns.of(reopened), columns);
    }

    @Test
    public void namesFromOlderFilesAreKept() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        SpreadsheetColumns.addTo(dataStore, List.of(new AnnotationColumn("2nd pass (mother)", List.of("x"), false,
            List.of())));

        assertTrue(dataStore.getVariable("2nd pass (mother)") != null);
    }
}

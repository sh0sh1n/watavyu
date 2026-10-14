package org.datavyu.models.db;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import java.util.List;

/** New cells are point cells; an offset before the onset is kept, and the spreadsheet flags it. */
public class PointCellTest {

    @Test
    public void aNewCellIsAPointCell() throws Exception {
        Variable column = DataStoreFactory.newDataStore().createVariable("Column", Argument.Type.TEXT);

        Cell cell = column.createPointCell(5_000);

        assertEquals(cell.getOnset(), 5_000L);
        assertEquals(cell.getOffset(), 5_000L);
        assertEquals(column.getCells(), List.of(cell));
    }

    @Test
    public void anOffsetBeforeTheOnsetIsKept() throws Exception {
        Cell cell = DataStoreFactory.newDataStore().createVariable("Column", Argument.Type.TEXT).createPointCell(5_000);

        cell.setOffset(1_000);
        assertEquals(cell.getOffset(), 1_000L);

        cell.setOnset(9_000);
        assertEquals(cell.getOffset(), 1_000L);
    }
}

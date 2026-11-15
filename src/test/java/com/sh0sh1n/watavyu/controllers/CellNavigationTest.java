package com.sh0sh1n.watavyu.controllers;

import static org.testng.Assert.assertEquals;

import com.sh0sh1n.watavyu.models.Cell;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

/**
 * Where ⌃J ⌃K ⌃H ⌃L go (AGENTS.md › Decisions, 2026-11-13): up and down a column by time, or from the playhead with no
 * cell selected; to the column beside, passing over media columns, landing on the cell whose onset is nearest.
 */
public class CellNavigationTest {

    private DataStore dataStore;
    private Column gaze;
    private Cell early;
    private Cell middle;
    private Cell late;

    @BeforeMethod
    public void setUp() throws Exception {
        dataStore = DataStoreFactory.newDataStore();
        gaze = dataStore.createColumn("gaze");
        late = gaze.createPointCell(3_000);
        early = gaze.createPointCell(1_000);
        middle = gaze.createPointCell(2_000);
    }

    private List<Cell> cells() {
        return gaze.getCellsTemporally();
    }

    @Test
    public void downAndUpGoToTheNextAndPreviousCellInTime() {
        assertEquals(CellNavigation.vertical(cells(), middle, true, 0), Optional.of(late));
        assertEquals(CellNavigation.vertical(cells(), middle, false, 0), Optional.of(early));
    }

    @Test
    public void theEndsOfTheColumnGoNoFurther() {
        assertEquals(CellNavigation.vertical(cells(), late, true, 0), Optional.empty());
        assertEquals(CellNavigation.vertical(cells(), early, false, 0), Optional.empty());
    }

    /** With no cell selected, down goes to the first cell starting at or after the playhead, up to the last before. */
    @Test
    public void withNoCellTheyGoFromThePlayhead() {
        assertEquals(CellNavigation.vertical(cells(), null, true, 1_500), Optional.of(middle));
        assertEquals(CellNavigation.vertical(cells(), null, true, 2_000), Optional.of(middle), "at counts");
        assertEquals(CellNavigation.vertical(cells(), null, false, 2_500), Optional.of(middle));
        assertEquals(CellNavigation.vertical(cells(), null, false, 2_000), Optional.of(middle), "at counts");
        assertEquals(CellNavigation.vertical(cells(), null, true, 3_500), Optional.empty());
        assertEquals(CellNavigation.vertical(cells(), null, false, 500), Optional.empty());
        assertEquals(CellNavigation.vertical(List.of(), null, true, 0), Optional.empty());
    }

    @Test
    public void besideGoesLeftAndRightPassingOverMediaColumnsAndStopsAtTheEdges() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        Column speech = dataStore.createColumn("speech");
        List<Column> shown = List.of(gaze, media, speech);

        assertEquals(CellNavigation.beside(shown, gaze, true), Optional.of(speech));
        assertEquals(CellNavigation.beside(shown, speech, false), Optional.of(gaze));
        assertEquals(CellNavigation.beside(shown, speech, true), Optional.empty());
        assertEquals(CellNavigation.beside(shown, gaze, false), Optional.empty());
        assertEquals(CellNavigation.beside(shown, media, true), Optional.of(speech), "from a media column too");
    }

    /** With no column selected, right goes to the first annotation column and left to the last. */
    @Test
    public void withNoColumnBesideGoesToTheFirstOrLast() throws Exception {
        Column media = dataStore.createMediaColumn("media_1");
        Column speech = dataStore.createColumn("speech");
        List<Column> shown = List.of(media, gaze, speech, dataStore.createMediaColumn("media_2"));

        assertEquals(CellNavigation.beside(shown, null, true), Optional.of(gaze));
        assertEquals(CellNavigation.beside(shown, null, false), Optional.of(speech));
        assertEquals(CellNavigation.beside(List.of(media), null, true), Optional.empty());
    }

    @Test
    public void nearestIsTheCellWhoseOnsetIsNearestAndTheEarlierOfTwo() {
        assertEquals(CellNavigation.nearest(cells(), 2_400), Optional.of(middle));
        assertEquals(CellNavigation.nearest(cells(), 2_500), Optional.of(middle), "the earlier of two as near");
        assertEquals(CellNavigation.nearest(cells(), 10_000), Optional.of(late));
        assertEquals(CellNavigation.nearest(List.of(), 0), Optional.empty());
    }
}

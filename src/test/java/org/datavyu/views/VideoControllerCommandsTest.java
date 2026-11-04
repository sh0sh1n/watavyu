package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import org.datavyu.TestSpreadsheets;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** The keypad and the menu bar run the controls drawer's commands by name, each as its key in the drawer does. */
public class VideoControllerCommandsTest {

    private ProjectController spreadsheet;
    private VideoController controller;

    @BeforeMethod
    public void setUp() {
        spreadsheet = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore());
        controller = spreadsheet.getVideoController();
        controller.setCurrentTime(20_000);
    }

    /** One key in the drawer jumps; the keypad's minus jumps back by the jump time and Shift with it forward. */
    @Test
    public void jumpBackAndForwardShareTheDrawersKey() {
        controller.run(Command.JUMP_BACK);
        assertEquals(controller.getCurrentTime(), 15_000L);

        controller.run(Command.JUMP_FORWARD);
        controller.run(Command.JUMP_FORWARD);
        assertEquals(controller.getCurrentTime(), 25_000L);
    }

    @Test
    public void findGoesToTheOnsetOrOffsetFieldsTime() {
        controller.setOnsetField(3_000);
        controller.setOffsetField(7_000);

        controller.run(Command.FIND_ONSET);
        assertEquals(controller.getCurrentTime(), 3_000L);

        controller.run(Command.FIND_OFFSET);
        assertEquals(controller.getCurrentTime(), 7_000L);
    }

    @Test
    public void newCellGoesInTheSelectedColumnAtTheCurrentTime() throws Exception {
        Variable gaze = spreadsheet.getDataStore().createVariable("gaze");
        gaze.setSelected(true);

        controller.run(Command.NEW_CELL_IN_SELECTED_COLUMN);

        assertEquals(gaze.getCells().size(), 1);
        assertEquals(gaze.getCells().get(0).getOnset(), 20_000L);
    }

    @Test
    public void aCommandTheDrawerDoesNotHaveIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> controller.run(Command.SAVE));
        assertThrows(IllegalArgumentException.class, () -> controller.run(Command.SHOW_CONTROLS));
    }
}

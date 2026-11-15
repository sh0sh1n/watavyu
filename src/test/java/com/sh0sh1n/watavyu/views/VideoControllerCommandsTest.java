package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertThrows;

import com.sh0sh1n.watavyu.TestSpreadsheets;
import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Column;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import java.awt.Container;
import javax.swing.JButton;

/** The keypad runs the controls drawer's commands by name, as the drawer's keys do. */
public class VideoControllerCommandsTest {

    private ProjectController spreadsheet;
    private VideoController controller;

    @BeforeMethod
    public void setUp() {
        spreadsheet = TestSpreadsheets.withPlayback(DataStoreFactory.newDataStore());
        controller = spreadsheet.getVideoController();
        controller.setCurrentTime(20_000);
    }

    /** The keypad's minus jumps back by the jump time, and Shift with it forward. */
    @Test
    public void jumpBackAndForwardByTheJumpTime() {
        controller.run(Command.JUMP_BACK);
        assertEquals(controller.getCurrentTime(), 15_000L);

        controller.run(Command.JUMP_FORWARD);
        controller.run(Command.JUMP_FORWARD);
        assertEquals(controller.getCurrentTime(), 25_000L);
    }

    /** The drawer's key jumps back whichever way the keypad last jumped. */
    @Test
    public void theDrawersJumpKeyJumpsBackAfterAJumpForward() {
        controller.run(Command.JUMP_FORWARD);

        key("jumpByButton").doClick();

        assertEquals(controller.getCurrentTime(), 20_000L);
    }

    /** Shift with the drawer's find key goes to the offset; a find run by name goes where its name says. */
    @Test
    public void findByNameIgnoresShiftAndTheDrawersKeyHeedsIt() {
        controller.setOnsetField(3_000);
        controller.setOffsetField(7_000);
        controller.setShiftMask(true);

        controller.run(Command.FIND_ONSET);
        assertEquals(controller.getCurrentTime(), 3_000L);

        key("findButton").doClick();
        assertEquals(controller.getCurrentTime(), 7_000L);
    }

    private JButton key(final String name) {
        return find(controller.getControls(), name);
    }

    private static JButton find(final Container container, final String name) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && name.equals(button.getName())) {
                return button;
            }
            if (child instanceof Container inner) {
                JButton found = find(inner, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
        Column gaze = spreadsheet.getDataStore().createColumn("gaze");
        gaze.setSelected(true);

        controller.run(Command.NEW_CELL_IN_SELECTED_COLUMN);

        assertEquals(gaze.getCells().size(), 1);
        assertEquals(gaze.getCells().get(0).getOnset(), 20_000L);
    }

    /** A key run by name shows as pressed for a moment, without holding the event thread, and runs once. */
    @Test
    public void aKeyRunByNameShowsThePressAndRunsOnce() throws Exception {
        JButton reset = key("resetRateButton");
        List<String> ran = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> reset.addActionListener(e -> ran.add("reset")));

        // Still shown pressed once the command has run: the press is not held on the event thread.
        boolean[] pressed = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            controller.run(Command.RESET_RATE);
            pressed[0] = reset.getModel().isPressed();
        });
        assertTrue(pressed[0]);
        assertEquals(ran, List.of("reset"));

        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> pressed[0] = reset.getModel().isPressed());
        assertFalse(pressed[0]);
        assertEquals(ran, List.of("reset"), "letting go does not run it again");
    }

    @Test
    public void aCommandTheDrawerDoesNotHaveIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> controller.run(Command.SAVE));
        assertThrows(IllegalArgumentException.class, () -> controller.run(Command.SHOW_CONTROLS));
    }
}

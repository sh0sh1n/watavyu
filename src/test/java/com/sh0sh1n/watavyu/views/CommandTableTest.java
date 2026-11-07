package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.Action;
import java.util.ArrayList;
import java.util.List;

/** Each command has one action, whose label, check mark and whether it can run follow the state it is refreshed from. */
public class CommandTableTest {

    private CommandTable commands;
    private List<String> ran;
    private int cells;
    private boolean temporal;
    private int gathered;

    @BeforeMethod
    public void setUp() {
        commands = new CommandTable(() -> gathered++);
        ran = new ArrayList<>();
        cells = 0;
        temporal = true;
        gathered = 0;
    }

    @Test
    public void aCommandIsNamedByItsLabelUntilItGivesItsOwn() {
        commands.define(Command.SAVE, () -> ran.add("save"));
        commands.define(Command.DELETE_CELLS, () -> { }).labelled(() -> "Delete " + cells + " Cells");

        commands.refresh();
        assertEquals(commands.get(Command.SAVE).getValue(Action.NAME), Command.SAVE.label());
        assertEquals(commands.get(Command.DELETE_CELLS).getValue(Action.NAME), "Delete 0 Cells");
        assertNull(commands.get(Command.SAVE).getValue(Action.SELECTED_KEY), "not a check box");
        assertTrue(commands.get(Command.SAVE).isEnabled());
    }

    @Test
    public void stateIsWorkedOutAgainOnEachRefresh() {
        Action delete = commands.define(Command.DELETE_CELLS, () -> { })
            .labelled(() -> "Delete " + cells + " Cells").enabledWhen(() -> cells > 0);
        Action temporalLayout = commands.define(Command.TEMPORAL_LAYOUT, () -> { }).checked(() -> temporal);
        commands.refresh();
        assertFalse(delete.isEnabled());
        assertEquals(temporalLayout.getValue(Action.SELECTED_KEY), true);

        cells = 2;
        temporal = false;
        assertFalse(delete.isEnabled(), "state changes only on a refresh");
        commands.refresh();

        assertTrue(delete.isEnabled());
        assertEquals(delete.getValue(Action.NAME), "Delete 2 Cells");
        assertEquals(temporalLayout.getValue(Action.SELECTED_KEY), false);
    }

    /** A command can change any command's state, such as an edit enabling Undo, so all are refreshed after it runs. */
    @Test
    public void runningACommandRefreshesEveryCommand() {
        Action delete = commands.define(Command.DELETE_CELLS, () -> { }).enabledWhen(() -> cells > 0);
        commands.define(Command.NEW_CELL, () -> cells++);

        commands.get(Command.NEW_CELL).actionPerformed(null);

        assertEquals(cells, 1);
        assertTrue(delete.isEnabled());
    }

    /** State that several commands read is gathered once, before any command's is worked out from it. */
    @Test
    public void eachRefreshFirstGathersTheStateOnce() {
        commands.define(Command.DELETE_CELLS, () -> { }).labelled(() -> "Delete " + gathered);
        commands.define(Command.NEW_CELL, () -> { }).enabledWhen(() -> gathered > 0);

        commands.refresh();

        assertEquals(gathered, 1);
        assertEquals(commands.get(Command.DELETE_CELLS).getValue(Action.NAME), "Delete 1");
        assertTrue(commands.get(Command.NEW_CELL).isEnabled());
    }

    /** The main window defines its commands before the tabs whose state they show exist. */
    @Test
    public void stateIsNotWorkedOutUntilTheFirstRefresh() {
        Action delete = commands.define(Command.DELETE_CELLS, () -> { })
            .labelled(() -> { throw new IllegalStateException("too soon"); })
            .enabledWhen(() -> { throw new IllegalStateException("too soon"); });

        assertEquals(delete.getValue(Action.NAME), Command.DELETE_CELLS.label());
    }

    @Test
    public void aCommandIsDefinedOnceAndMustBeDefinedToBeUsed() {
        commands.define(Command.SAVE, () -> { });

        assertThrows(IllegalStateException.class, () -> commands.define(Command.SAVE, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> commands.get(Command.OPEN));
    }
}

package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.datavyu.TestSpreadsheets;
import org.datavyu.controllers.SaveController;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.models.project.Project;
import org.datavyu.views.discrete.SpreadSheetPanel;
import org.datavyu.views.discrete.layouts.SheetLayoutFactory;
import org.datavyu.views.discrete.layouts.SheetLayoutFactory.SheetLayoutType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/**
 * The main window holds one tab per open spreadsheet. The one open at launch has no unsaved changes, an untitled
 * spreadsheet with no changes gives way to a project opened, once it has opened, and each tab's title shows its own
 * project, the window's the selected one's.
 */
public class DatavyuViewTest {

    private JFrame frame;
    private DatavyuView view;
    private Path folder;

    @BeforeMethod
    public void setUp() throws Exception {
        folder = Files.createTempDirectory("study");
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame();
            view = TestSpreadsheets.mainWindow(frame);
        });
    }

    @AfterMethod
    public void tearDown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tabs = view.getTabbedPane();
            while (tabs.getTabCount() > 0) {
                view.removeTab((SpreadSheetPanel) tabs.getComponentAt(0));
            }
            frame.dispose();
        });
    }

    /** Writes a project file holding one column. */
    private File projectFile(final String name, final String column) throws Exception {
        ProjectController spreadsheet = new ProjectController(new Project(), DataStoreFactory.newDataStore());
        spreadsheet.getDataStore().createVariable(column);
        File file = folder.resolve(name + ".watavyu.json").toFile();
        new SaveController().saveProject(file, spreadsheet.toDocument(folder), false);
        return file;
    }

    /** Something done on the EDT that may throw. */
    private interface Step {
        void run() throws Exception;
    }

    private void onEdt(final Step step) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                step.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        // Titles are shown on the EDT after the change that prompts them.
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** F4: spreadsheets open in temporal layout, so media clips line up with the cells coded from them. */
    @Test
    public void spreadsheetsOpenInTemporalLayout() {
        assertEquals(SheetLayoutFactory.DEFAULT_LAYOUT, SheetLayoutType.WeakTemporal);
    }

    @Test
    public void theTitleNamesTheApplicationAndTheProject() {
        assertEquals(DatavyuView.windowTitle("Watavyu", "study.watavyu.json  *", false), "Watavyu - study.watavyu.json  *");
    }

    @Test
    public void theTitleShowsQuickKeyMode() {
        assertEquals(DatavyuView.windowTitle("Watavyu", "study.watavyu.json  ", true), "Watavyu - study.watavyu.json   <QUICK KEY MODE>");
    }

    /** B8: a recovered project is named after the project that was autosaved. */
    @Test
    public void aRecoveredProjectIsNamedAfterTheOriginal() {
        assertEquals(DatavyuView.recoveredName("~study_12005522346316252883.watavyu.json"), "study");
        assertEquals(DatavyuView.recoveredName("~session_04_5415962468724307690.watavyu.json"), "session_04");
        assertEquals(DatavyuView.recoveredName("~noname_42.watavyu.json"), "noname");
    }

    /** It has its empty media columns but no unsaved changes, so it is not autosaved and gives way to a project. */
    @Test
    public void theSpreadsheetOpenAtLaunchHasNoUnsavedChanges() throws Exception {
        onEdt(() -> view.startFirstSpreadsheet());

        assertFalse(view.selectedController().isChanged());
    }

    @Test
    public void anUntitledUnchangedSpreadsheetGivesWayToTheProjectOpened() throws Exception {
        File study = projectFile("study", "gaze");

        onEdt(() -> view.open(study));

        assertEquals(view.getTabbedPane().getTabCount(), 1);
        assertTrue(view.selectedController().getDataStore().getVariable("gaze") != null);
    }

    @Test
    public void anUntitledSpreadsheetWithChangesStays() throws Exception {
        File study = projectFile("study", "gaze");
        ProjectController untitled = view.selectedController();

        onEdt(() -> {
            untitled.getDataStore().createVariable("notes");
            view.open(study);
        });

        assertEquals(view.getTabbedPane().getTabCount(), 2);
        assertSame(((SpreadSheetPanel) view.getTabbedPane().getComponentAt(0)).getProjectController(), untitled);
    }

    @Test
    public void aProjectThatDoesNotOpenLeavesTheUntitledSpreadsheet() throws Exception {
        ProjectController untitled = view.selectedController();

        onEdt(() -> view.open(folder.resolve("missing.watavyu.json").toFile()));

        assertEquals(view.getTabbedPane().getTabCount(), 1);
        assertSame(view.selectedController(), untitled);
    }

    @Test
    public void eachTabIsTitledWithItsOwnProjectAndTheWindowWithTheSelectedOne() throws Exception {
        ProjectController untitled = view.selectedController();
        onEdt(() -> {
            untitled.getDataStore().createVariable("notes");
            view.open(projectFile("study", "gaze"));
        });
        ProjectController study = view.selectedController();
        JTabbedPane tabs = view.getTabbedPane();
        assertTrue(tabs.getTitleAt(0).startsWith("untitled"), tabs.getTitleAt(0));
        assertTrue(tabs.getTitleAt(1).startsWith("study"), tabs.getTitleAt(1));
        assertTrue(frame.getTitle().contains("study"), frame.getTitle());

        // A change in the tab behind marks that tab, not the window.
        String windowTitle = frame.getTitle();
        onEdt(() -> untitled.getDataStore().createVariable("speech"));
        assertTrue(tabs.getTitleAt(0).endsWith("*"), tabs.getTitleAt(0));
        assertFalse(tabs.getTitleAt(1).endsWith("*"), tabs.getTitleAt(1));
        assertEquals(frame.getTitle(), windowTitle);

        onEdt(() -> tabs.setSelectedIndex(0));
        assertTrue(frame.getTitle().contains("untitled"), frame.getTitle());
        assertNull(study.getDataStore().getVariable("speech"));
    }

    /** Opens a second tab, after the untitled one, which has a change so it stays. */
    private ProjectController secondTab() throws Exception {
        ProjectController untitled = view.selectedController();
        onEdt(() -> {
            untitled.getDataStore().createVariable("notes");
            view.open(projectFile("study", "gaze"));
        });
        return view.selectedController();
    }

    private void select(final int tab) throws Exception {
        onEdt(() -> view.getTabbedPane().setSelectedIndex(tab));
    }

    private void run(final Command command) throws Exception {
        onEdt(() -> view.action(command).actionPerformed(null));
    }

    /** B20: each tab has its own highlight-and-focus mode, and the menu item names the selected tab's. */
    @Test
    public void highlightAndFocusNamesTheSelectedTabsMode() throws Exception {
        ProjectController study = secondTab();
        Action highlight = view.action(Command.HIGHLIGHT_AND_FOCUS);
        assertEquals(highlight.getValue(Action.NAME), "Disable Highlight and Focus Mode");

        run(Command.HIGHLIGHT_AND_FOCUS);
        assertFalse(study.getVideoController().getCellHighlightAndFocus());
        assertEquals(highlight.getValue(Action.NAME), "Enable Highlight and Focus Mode");

        select(0);
        assertEquals(highlight.getValue(Action.NAME), "Disable Highlight and Focus Mode");
        select(1);
        assertEquals(highlight.getValue(Action.NAME), "Enable Highlight and Focus Mode");
    }

    /** Each tab has its own layout, and the menu's check mark shows the selected tab's. */
    @Test
    public void temporalLayoutIsCheckedForTheSelectedTab() throws Exception {
        secondTab();
        Action temporal = view.action(Command.TEMPORAL_LAYOUT);
        assertEquals(temporal.getValue(Action.SELECTED_KEY), true);

        run(Command.TEMPORAL_LAYOUT);
        assertEquals(view.getSpreadsheetPanel().getLayoutType(), SheetLayoutType.Ordinal);
        assertEquals(temporal.getValue(Action.SELECTED_KEY), false);

        select(0);
        assertEquals(view.getSpreadsheetPanel().getLayoutType(), SheetLayoutType.WeakTemporal);
        assertEquals(temporal.getValue(Action.SELECTED_KEY), true);
    }

    /** A new tab opens in the layout of the tab selected before it, as the menu showed it. */
    @Test
    public void aNewTabTakesTheSelectedTabsLayout() throws Exception {
        run(Command.TEMPORAL_LAYOUT);

        secondTab();

        assertEquals(view.getSpreadsheetPanel().getLayoutType(), SheetLayoutType.Ordinal);
        assertEquals(view.action(Command.TEMPORAL_LAYOUT).getValue(Action.SELECTED_KEY), false);
    }

    /** Undo names the selected tab's last edit, and is disabled in a tab with none. */
    @Test
    public void undoNamesTheSelectedTabsEdit() throws Exception {
        ProjectController study = secondTab();
        Action undo = view.action(Command.UNDO);
        onEdt(() -> study.columns().newColumn("speech"));
        assertEquals(undo.getValue(Action.NAME), "Undo New Column \"speech\"");
        assertTrue(undo.isEnabled());

        select(0);
        assertEquals(undo.getValue(Action.NAME), "Undo");
        assertFalse(undo.isEnabled());
    }

    /** Delete Cells and New Cell to Left and Right count the selected tab's selected cells, to say Cell or Cells. */
    @Test
    public void cellCommandsCountTheSelectedTabsSelection() throws Exception {
        ProjectController study = secondTab();
        Action delete = view.action(Command.DELETE_CELLS);
        Action left = view.action(Command.NEW_CELL_LEFT);
        Action right = view.action(Command.NEW_CELL_RIGHT);
        List<Cell> cells = new ArrayList<>();
        onEdt(() -> {
            Variable gaze = study.getDataStore().getVariable("gaze");
            Variable speech = study.getDataStore().createVariable("speech");
            cells.add(gaze.createPointCell(1_000));
            cells.add(gaze.createPointCell(2_000));
            cells.add(speech.createPointCell(1_000));
            cells.forEach(cell -> cell.setSelected(false));
            view.refreshCommands();
        });
        assertFalse(delete.isEnabled());
        assertFalse(left.isEnabled());
        assertFalse(right.isEnabled());

        onEdt(() -> {
            cells.get(0).setSelected(true);
            view.refreshCommands();
        });
        assertEquals(delete.getValue(Action.NAME), "Delete Cell");
        assertTrue(delete.isEnabled());
        assertFalse(left.isEnabled(), "gaze is the first column");
        assertEquals(right.getValue(Action.NAME), "New Cell to Right");
        assertTrue(right.isEnabled());

        onEdt(() -> {
            cells.get(1).setSelected(true);
            cells.get(2).setSelected(true);
            view.refreshCommands();
        });
        assertEquals(delete.getValue(Action.NAME), "Delete Cells");
        assertEquals(left.getValue(Action.NAME), "New Cell to Left", "only the speech cell has a column to its left");
        assertEquals(right.getValue(Action.NAME), "New Cells to Right");

        select(0);
        assertFalse(delete.isEnabled(), "the untitled tab has no cells selected");
    }

    /** New Cell and Export need a column to show: they are disabled in a tab whose columns are all hidden. */
    @Test
    public void newCellAndExportNeedAVisibleColumn() throws Exception {
        ProjectController study = secondTab();
        Action newCell = view.action(Command.NEW_CELL);
        Action export = view.action(Command.EXPORT_JSON);
        assertTrue(newCell.isEnabled());
        assertTrue(export.isEnabled());

        onEdt(() -> {
            study.columns().hide(study.getDataStore().getAllVariables());
            view.refreshCommands();
        });

        assertFalse(newCell.isEnabled());
        assertFalse(export.isEnabled());
    }
}

package com.sh0sh1n.watavyu;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.controllers.Autosave;
import com.sh0sh1n.watavyu.views.MainWindow;
import com.sh0sh1n.watavyu.views.spreadsheet.SpreadSheetPanel;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/** B11: once quitting is agreed, every spreadsheet's work is saved or knowingly discarded, so its autosave goes. */
public class QuittingTest {

    @Test
    public void quittingDeletesEachSpreadsheetsAutosave() throws Exception {
        Path folder = Files.createTempDirectory("autosaves");
        Autosave autosave = Autosave.create(folder);
        autosave.write("{\"format\": \"watavyu\", \"version\": 1, \"columns\": []}\n", null, null);
        AtomicBoolean quit = new AtomicBoolean();
        JFrame frame = new JFrame();
        AtomicReference<MainWindow> made = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                Watavyu app = new Watavyu();
                MainWindow window = new MainWindow(app, frame, folder);
                made.set(window);
                app.setWindow(window);
                // A spreadsheet with no unsaved changes, so nothing is asked, that holds an autosave.
                window.selectedController().getAutoSave().adopt(autosave);
                quit.set(app.safeQuit());
            });

            assertTrue(quit.get());
            assertFalse(Files.exists(autosave.getFile()));
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                JTabbedPane tabs = made.get().getTabbedPane();
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    ((SpreadSheetPanel) tabs.getComponentAt(i)).close();
                }
                frame.dispose();
            });
        }
    }
}

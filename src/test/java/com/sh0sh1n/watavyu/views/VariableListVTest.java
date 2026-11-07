package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.models.db.DataStore;
import com.sh0sh1n.watavyu.models.db.DataStoreFactory;
import com.sh0sh1n.watavyu.models.db.Variable;
import com.sh0sh1n.watavyu.models.project.Project;
import org.testng.annotations.Test;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;

/** The Column List shows and hides columns, and shows them all; columns are renamed only in the Code Editor. */
public class VariableListVTest {

    static <T extends Component> T find(final Container in, final Class<T> type, final String name) {
        for (Component child : in.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = find(container, type, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void namesCannotBeEditedButVisibilityCan() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createVariable("notes");
        AtomicReference<JTable> table = new AtomicReference<>();
        AtomicReference<VariableListV> list = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            list.set(new VariableListV(null, false, new ProjectController(new Project(), dataStore)));
            table.set(find(list.get().getContentPane(), JTable.class, "variableList"));
        });
        try {
            JTable rows = table.get();
            assertEquals(rows.getRowCount(), 1);
            assertEquals(rows.getModel().getValueAt(0, 2), "notes");
            assertFalse(rows.getModel().isCellEditable(0, 2), "name");
            assertFalse(rows.getModel().isCellEditable(0, 0), "order");
            assertTrue(rows.getModel().isCellEditable(0, 1), "visible");
        } finally {
            SwingUtilities.invokeAndWait(() -> list.get().dispose());
        }
    }

    @Test
    public void showAllColumnsShowsEveryHiddenColumn() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable notes = dataStore.createVariable("notes");
        Variable media = dataStore.createMediaVariable("media_1");
        Variable shown = dataStore.createVariable("shown");
        notes.setHidden(true);
        media.setHidden(true);
        AtomicReference<VariableListV> list = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            list.set(new VariableListV(null, false, new ProjectController(new Project(), dataStore)));
            JButton showAll = find(list.get().getContentPane(), JButton.class, "showAllColumns");
            assertEquals(showAll.getText(), "Show All Columns");
            showAll.doClick();
        });
        try {
            assertFalse(notes.isHidden());
            assertFalse(media.isHidden());
            assertFalse(shown.isHidden());
        } finally {
            SwingUtilities.invokeAndWait(() -> list.get().dispose());
        }
    }
}

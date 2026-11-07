package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.controllers.ProjectController;
import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.DataStoreFactory;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.models.Project;
import org.testng.annotations.Test;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;

/** The Column List shows and hides columns, and shows them all; columns are renamed only in the Code Editor. */
public class ColumnListVTest {

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
        dataStore.createColumn("notes");
        AtomicReference<JTable> table = new AtomicReference<>();
        AtomicReference<ColumnListV> list = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            list.set(new ColumnListV(null, false, new ProjectController(new Project(), dataStore)));
            table.set(find(list.get().getContentPane(), JTable.class, "columnList"));
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
        Column notes = dataStore.createColumn("notes");
        Column media = dataStore.createMediaColumn("media_1");
        Column shown = dataStore.createColumn("shown");
        notes.setHidden(true);
        media.setHidden(true);
        AtomicReference<ColumnListV> list = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            list.set(new ColumnListV(null, false, new ProjectController(new Project(), dataStore)));
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

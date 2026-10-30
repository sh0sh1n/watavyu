package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.testng.annotations.Test;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicReference;

/** The Column List shows and hides columns; columns are renamed only in the Code Editor. */
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
            list.set(new VariableListV(null, false, dataStore));
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
}

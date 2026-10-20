package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.testng.annotations.Test;

import javax.swing.JCheckBoxMenuItem;

/** A media column's header menu locks and unlocks its clips. */
public class LockClipsTest {

    @Test
    public void lockClipsTogglesTheColumnsLock() throws Exception {
        Variable media = DataStoreFactory.newDataStore().createMediaVariable("media_1");
        int[] changes = {0};

        JCheckBoxMenuItem item = SpreadsheetColumn.lockItem(media, () -> changes[0]++);
        assertEquals(item.getText(), "Lock Clips");
        assertFalse(item.isSelected());

        item.doClick();
        assertTrue(media.isLocked());
        assertEquals(changes[0], 1);

        JCheckBoxMenuItem again = SpreadsheetColumn.lockItem(media, () -> changes[0]++);
        assertTrue(again.isSelected());
        again.doClick();
        assertFalse(media.isLocked());
    }
}

package com.sh0sh1n.watavyu.views;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** The About window. */
public class AboutVTest {

    @Test
    public void closingItDisposesOfIt() throws Exception {
        AtomicInteger operation = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            AboutV about = new AboutV(null, false);
            try {
                operation.set(about.getDefaultCloseOperation());
            } finally {
                about.dispose();
            }
        });
        assertEquals(operation.get(), WindowConstants.DISPOSE_ON_CLOSE);
    }
}

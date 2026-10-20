package org.datavyu.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** A media column's window shows the video of the clip it is told to, and black for a gap or a clip without video. */
public class MediaWindowTest {

    /** @return the one card the window shows */
    private static Component shown(final MediaWindow window) {
        Container content = (Container) window.getContentPane().getComponent(0);
        Component[] visible = Arrays.stream(content.getComponents()).filter(Component::isVisible)
            .toArray(Component[]::new);
        assertEquals(visible.length, 1);
        return visible[0];
    }

    private static boolean isGap(final Component card) {
        return card instanceof JPanel panel && panel.getComponentCount() == 0
            && Color.BLACK.equals(panel.getBackground());
    }

    @Test
    public void showsTheClipInPlayAndBlackElsewhere() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MediaWindow window = new MediaWindow(null, "media_1");
            try {
                JPanel first = new JPanel();
                JPanel second = new JPanel();
                Object a = new Object();
                Object b = new Object();
                window.addClip(a, first);
                window.addClip(b, second);
                assertEquals(window.getTitle(), "media_1");

                window.showClip(b);
                assertTrue(shown(window) == second);

                window.showClip(a);
                assertTrue(shown(window) == first);

                window.showClip(null);
                assertTrue(isGap(shown(window)));

                // A clip whose file is missing has no video in the window.
                window.showClip(new Object());
                assertTrue(isGap(shown(window)));

                window.showClip(a);
                window.removeClip(a);
                assertTrue(isGap(shown(window)));
                assertFalse(Arrays.asList(((Container) window.getContentPane().getComponent(0)).getComponents())
                    .contains(first));

                window.addClip(new Object(), null);
                assertTrue(isGap(shown(window)));
            } finally {
                window.close();
            }
        });
    }

    /** A media window with no remembered place opens below and to the right of those already open. */
    @Test
    public void newWindowsCascade() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MediaWindow first = new MediaWindow(null, "cascade_1");
            MediaWindow second = new MediaWindow(null, "cascade_2");
            try {
                assertEquals(second.getX() - first.getX(), MediaWindow.CASCADE_STEP);
                assertEquals(second.getY() - first.getY(), MediaWindow.CASCADE_STEP);
            } finally {
                first.close();
                second.close();
            }
        });
    }
}

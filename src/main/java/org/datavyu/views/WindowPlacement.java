package org.datavyu.views;

import org.datavyu.Datavyu;
import org.datavyu.util.Settings;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where windows appear. The main window, the video controller and each video window come back where the user left
 * them; their bounds are kept in {@link Settings} under a key per window. Other windows are centred on the window
 * that owns them.
 */
public final class WindowPlacement {

    /** Pixels of a remembered window that must be on a screen for it to be put back there. */
    static final int MINIMUM_VISIBLE = 50;

    private WindowPlacement() {
    }

    /**
     * Shows a window that is not remembered: laid out at its preferred size and centred on its owner the first time,
     * where it was left after that.
     *
     * @param window The window to show.
     */
    public static void show(final Window window) {
        if (!window.isValid() || window.getWidth() == 0 || window.getHeight() == 0) {
            window.pack();
        }
        if (!window.isLocationByPlatform() && window.getX() == 0 && window.getY() == 0) {
            Window owner = window.getOwner();
            window.setLocationRelativeTo(owner != null ? owner : Datavyu.getApplication().getMainFrame());
        }
        window.setVisible(true);
    }

    /**
     * Puts a window back where it was last left, if that is still on a screen, and remembers where it is moved or
     * resized from now on. Call once, before the window is first shown.
     *
     * @param window      The window.
     * @param key         Identifies the window across runs.
     * @param restoreSize Whether to restore the size too, or only the location.
     * @return True if the window was placed from remembered bounds.
     */
    public static boolean restoreAndTrack(final Window window, final String key, final boolean restoreSize) {
        Optional<Rectangle> remembered = Settings.get().getWindowBounds(key);
        boolean restored = false;
        if (remembered.isPresent()) {
            Rectangle bounds = remembered.get();
            if (!restoreSize) {
                bounds = new Rectangle(bounds.x, bounds.y, window.getWidth(), window.getHeight());
            }
            if (isOnScreen(bounds, screenBounds())) {
                window.setBounds(bounds);
                restored = true;
            }
        }
        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(final ComponentEvent e) {
                remember();
            }

            @Override
            public void componentResized(final ComponentEvent e) {
                remember();
            }

            private void remember() {
                if (window.isShowing()) {
                    Settings.get().setWindowBounds(key, window.getBounds());
                }
            }
        });
        return restored;
    }

    /**
     * @param window  Window bounds.
     * @param screens Bounds of the screens.
     * @return True if at least {@value #MINIMUM_VISIBLE} by {@value #MINIMUM_VISIBLE} pixels of the window are on one
     * screen, so the user can see and grab it.
     */
    static boolean isOnScreen(final Rectangle window, final List<Rectangle> screens) {
        for (Rectangle screen : screens) {
            Rectangle visible = screen.intersection(window);
            if (visible.width >= MINIMUM_VISIBLE && visible.height >= MINIMUM_VISIBLE) {
                return true;
            }
        }
        return false;
    }

    private static List<Rectangle> screenBounds() {
        List<Rectangle> screens = new ArrayList<>();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            screens.add(device.getDefaultConfiguration().getBounds());
        }
        return screens;
    }
}

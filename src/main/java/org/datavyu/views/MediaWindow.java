package org.datavyu.views;

import org.datavyu.controllers.project.MediaPlayers;

import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Point;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

/**
 * The window of a media column. It shows the video of the clip in play, and black where no clip is: in the gaps
 * between clips, and for a clip whose file is missing or has no video.
 */
public final class MediaWindow extends JDialog implements MediaPlayers.ColumnWindow {

    private static final long serialVersionUID = 1L;

    private static final String GAP = "gap";

    /** How far each new media window is set off from the one before. */
    static final int CASCADE_STEP = 30;

    /** The media windows not yet closed; Swing EDT only. */
    private static final Set<MediaWindow> OPEN = new HashSet<>();

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<Object, String> names = new HashMap<>();
    private final Map<Object, Component> videos = new HashMap<>();
    private int nextName = 0;

    /**
     * @param owner  the main window
     * @param column the media column's name, which titles the window and keys where it is put back
     */
    public MediaWindow(final Frame owner, final String column) {
        super(owner, false);
        setTitle(column);
        setName("MediaWindow-" + column);
        JPanel gap = new JPanel();
        gap.setBackground(Color.BLACK);
        content.setBackground(Color.BLACK);
        content.add(gap, GAP);
        getContentPane().add(content);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        if (!WindowPlacement.restoreAndTrack(this, getName(), true)) {
            // Below and to the right of the media windows already open, so none hides another.
            Point origin = owner == null ? new Point(0, 0) : owner.getLocation();
            int step = CASCADE_STEP * (OPEN.size() + 1);
            setLocation(origin.x + step, origin.y + step);
        }
        OPEN.add(this);
    }

    @Override
    public void addClip(final Object clip, final Component video) {
        if (video == null) {
            return;
        }
        String name = "clip-" + nextName++;
        names.put(clip, name);
        videos.put(clip, video);
        content.add(video, name);
        if (names.size() == 1) {
            pack();
        }
    }

    @Override
    public void removeClip(final Object clip) {
        Component video = videos.remove(clip);
        if (names.remove(clip) != null) {
            content.remove(video);
            cards.show(content, GAP);
        }
    }

    @Override
    public void showClip(final Object clip) {
        String name = clip == null ? null : names.get(clip);
        cards.show(content, name == null ? GAP : name);
    }

    @Override
    public void setShowing(final boolean showing) {
        if (showing && !isVisible()) {
            WindowPlacement.show(this);
        } else if (!showing) {
            setVisible(false);
        }
    }

    @Override
    public void close() {
        OPEN.remove(this);
        dispose();
    }
}

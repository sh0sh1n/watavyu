package org.datavyu.views;

import org.datavyu.controllers.project.MediaPlayers;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.HashMap;
import java.util.Map;

/**
 * The window of a media column. It shows the video of the clip in play, and black where no clip is: in the gaps
 * between clips, and for a clip whose file is missing or has no video. Under the picture, a volume control sets the
 * volume of the clip in play; it is disabled while no clip is.
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

    /** The volume of the clip in play, in percent. */
    private final JSlider volume = new JSlider(0, 100, 100);

    /** Whether the volume control is being set to the clip in play's, rather than by the user. */
    private boolean showingVolume;

    private DoubleConsumer volumeListener = level -> { };

    private Runnable closeListener = () -> { };

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
        getContentPane().add(content, BorderLayout.CENTER);
        JPanel controls = new JPanel(new BorderLayout(4, 0));
        controls.add(new JLabel(new ImageIcon(MediaWindow.class.getResource("/icons/volume.png"))), BorderLayout.WEST);
        volume.setName("volume");
        volume.setToolTipText("Volume of the clip in play");
        volume.setEnabled(false);
        volume.addChangeListener(e -> {
            if (!showingVolume) {
                volumeListener.accept(volume.getValue() / 100.0);
            }
        });
        controls.add(volume, BorderLayout.CENTER);
        getContentPane().add(controls, BorderLayout.SOUTH);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                closeListener.run();
            }
        });
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
    public void showClip(final Object clip, final Double clipVolume) {
        String name = clip == null ? null : names.get(clip);
        cards.show(content, name == null ? GAP : name);
        showingVolume = true;
        try {
            volume.setEnabled(clipVolume != null);
            if (clipVolume != null) {
                volume.setValue((int) Math.round(clipVolume * 100));
            }
        } finally {
            showingVolume = false;
        }
    }

    @Override
    public void setVolumeListener(final DoubleConsumer listener) {
        volumeListener = listener;
    }

    @Override
    public void setCloseListener(final Runnable listener) {
        closeListener = listener;
    }

    /** @return the volume control, for tests */
    JSlider volumeControl() {
        return volume;
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

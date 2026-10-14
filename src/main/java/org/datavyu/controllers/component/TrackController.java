/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.controllers.component;

import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.event.component.CarriageEvent;
import org.datavyu.event.component.CarriageEvent.EventType;
import org.datavyu.event.component.CarriageEventListener;
import org.datavyu.models.Identifier;
import org.datavyu.models.component.MixerModel;
import org.datavyu.models.component.TrackConstants;
import org.datavyu.models.component.TrackModel;
import org.datavyu.models.component.TrackModel.TrackState;
import org.datavyu.models.component.ViewportState;
import org.datavyu.plugins.CustomActions;
import org.datavyu.plugins.ViewerStateListener;
import org.datavyu.views.component.TrackPainter;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.DecimalFormat;


/**
 * TrackPainterController is responsible for managing a TrackPainter
 */
public final class TrackController implements ViewerStateListener, PropertyChangeListener {

    /** Tooltip text for visible icon */
    private static final String VISIBLE_TOOLTIP = "Toggle video visibility";

    /** Tooltip text for lock/unlock icon */
    private static final String LOCK_UNLOCK_TOOLTIP = "Toggle lock/unlock";

    /** Tooltip text for rubbish icon */
    private static final String UNLOAD_TOOLTIP = "Close video";

    /** The LogManager logger for this class */
    private static final Logger logger = LogManager.getLogger(TrackController.class);

    /** Main panel holding the track UI */
    private final JPanel view;

    /** Header block */
    private final JPanel header;

    /** Track label */
    private final JLabel trackLabel;

    /** Label holding the icon for the plugin */
    private final JLabel iconLabel;

    /** Label holding the frame rate for the plugin */
    private final JLabel frameRateLabel;

    /** Component that paints the track */
    private final TrackPainter trackPainter;

    /** Button for (un)locking the track */
    private final JButton lockUnlockButton;

    /** Button for unloading the track (and its associated plugin) */
    private final JButton unloadingButton;

    /** Button for hiding or showing the data viewer */
    private final JButton visibleButton;

    /** Mixer model */
    private final MixerModel mixerModel;

    /** Track model */
    private final TrackModel trackModel;

    //TODO: These may be better suited for a resource bundle and properties file, OR TrackConstants.java
    /** Listeners interested in carriage events on the track */
    private final EventListenerList listenerList;

    /** visible? */
    private boolean isViewerVisible = true;
    
    /**
     * Creates a new TrackController.
     *
     * @param trackPainter the track painter for this controller to manage.
     */
    public TrackController(final MixerModel mixerModel, final TrackPainter trackPainter) {
        view = new JPanel();
        view.setLayout(new MigLayout("fillx, ins 0", "[]0[]"));
        view.setBorder(BorderFactory.createLineBorder(TrackConstants.BORDER_COLOR, 1));

        this.trackPainter = trackPainter;

        this.mixerModel = mixerModel;
        trackModel = new TrackModel();
        trackModel.setState(TrackState.NORMAL);
        trackModel.setLocked(false);

        trackPainter.setMixerView(mixerModel);
        trackPainter.setTrackModel(trackModel);

        mixerModel.getViewportModel().addPropertyChangeListener(this);

        listenerList = new EventListenerList();

        final TrackPainterListener painterListener = new TrackPainterListener();
        trackPainter.addMouseListener(painterListener);
        trackPainter.addMouseMotionListener(painterListener);

        // Create the Header panel and its components
        trackLabel = new JLabel("", SwingConstants.CENTER);
        trackLabel.setName("trackLabel");
        trackLabel.setHorizontalAlignment(SwingConstants.CENTER);
        trackLabel.setHorizontalTextPosition(SwingConstants.CENTER);
        iconLabel = new JLabel("", SwingConstants.CENTER);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setHorizontalTextPosition(SwingConstants.CENTER);

        frameRateLabel = new JLabel("", SwingConstants.LEFT);
        frameRateLabel.setHorizontalAlignment(SwingConstants.LEFT);
        frameRateLabel.setHorizontalTextPosition(SwingConstants.LEFT);
        frameRateLabel.setFont(new Font(frameRateLabel.getName(), Font.PLAIN, 10));

        header = new JPanel(new MigLayout("ins 0, wrap 6"));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1,
                        TrackConstants.BORDER_COLOR),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        header.setBackground(Color.LIGHT_GRAY);

        // Normally I would use pushx instead of defining the width, but in this
        // case I defined the width because span combined with push makes the
        // first action icon cell push out as well. 136 was calculated from
        // 140 pixels minus 2 minus 2 (from the empty border defined above).
        header.add(trackLabel, "span 6, w 136!, center, growx");
        header.add(iconLabel, "span 2, w 45!, h 32!, center, growx");
        header.add(frameRateLabel, "span 4, w 91!, h 32!, left, growx");

        // Set up the button used for locking/unlocking track movement
        {
            lockUnlockButton = new JButton(TrackConstants.UNLOCK_ICON);
            lockUnlockButton.setName("lockUnlockButton");
            lockUnlockButton.setContentAreaFilled(false);
            lockUnlockButton.setBorderPainted(false);
            lockUnlockButton.setToolTipText(LOCK_UNLOCK_TOOLTIP);
            lockUnlockButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    handleLockUnlockButtonEvent(e);
                }
            });



            String layout = "cell 0 2, w " + TrackConstants.ACTION_BUTTON_WIDTH + "!, h " + TrackConstants.ACTION_BUTTON_HEIGHT + "!";
            header.add(lockUnlockButton, layout);
        }

        // Set up the button used for hiding/showing a track's data viewer
        {
            visibleButton = new JButton(TrackConstants.VIEWER_HIDE_ICON);
            visibleButton.setName("visibleButton");
            visibleButton.setContentAreaFilled(false);
            visibleButton.setBorderPainted(false);
            visibleButton.setToolTipText(VISIBLE_TOOLTIP);
            visibleButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    handleVisibleButtonEvent(e);
                }
            });



            String layout = "cell 1 2, w " + TrackConstants.ACTION_BUTTON_WIDTH + "!, h " + TrackConstants.ACTION_BUTTON_HEIGHT + "!";
            header.add(visibleButton, layout);
        }

        // Set up the button used for removing a track and its plugin
        {
            unloadingButton = new JButton(TrackConstants.DELETE_ICON);
            unloadingButton.setName("unloadingButton");
            unloadingButton.setContentAreaFilled(false);
            unloadingButton.setBorderPainted(false);
            unloadingButton.setToolTipText(UNLOAD_TOOLTIP);
            unloadingButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    handleDeleteButtonEvent(e);
                }
            });



            String layout = "cell 5 2, w " + TrackConstants.ACTION_BUTTON_WIDTH + "!, h " + TrackConstants.ACTION_BUTTON_HEIGHT + "!";
            header.add(unloadingButton, layout);
        }

        // Add the header to our layout.
        {


            String layout = "w " + TrackConstants.HEADER_WIDTH + "!, h " + TrackConstants.CARRIAGE_HEIGHT + "!";
            view.add(header, layout);
        }

        // Add the track carriage to our layout.
        {


            String layout = "pushx, growx, h " + TrackConstants.CARRIAGE_HEIGHT + "!";
            view.add(trackPainter, layout);
        }

        view.validate();
    }

    /**
     * Sets the track information to use.
     *
     * @param id Identifier to use
     * @param icon Icon to use with this track. {@code null} if no icon.
     * @param mediaPath Path to the media file
     * @param duration Duration of the data feed in milliseconds
     * @param offset Offset of the data feed in milliseconds
     */
    void setTrackInformation(final Identifier id, final ImageIcon icon, final File mediaPath,
                             final long duration, final long offset, final float frameRate) {

        if (icon != null) {
            iconLabel.setIcon(icon);
        }

        if (frameRate > 0) {
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            df.setMinimumFractionDigits(2);
            frameRateLabel.setText("FPS: " + df.format(frameRate));
        }

        final String trackName = mediaPath.getName();
        final String trackPath = mediaPath.getAbsolutePath();

        trackModel.setIdentifier(id);
        trackModel.setTrackName(trackName);
        trackModel.setSourceFile(trackPath);
        trackModel.setDuration(duration);
        trackModel.setOffset(offset);
        trackModel.setErroneous(false);
        trackLabel.setText(trackName);
        trackLabel.setToolTipText(trackName);
        trackPainter.setTrackModel(trackModel);
    }

    /**
     * Sets the track offset in milliseconds.
     *
     * @param offset Offset of the data feed in milliseconds
     */
    void setTrackOffset(final long offset) {
        trackModel.setOffset(offset);
        trackPainter.setTrackModel(trackModel);
    }

    private ImageIcon getVisibleButtonIcon() {

        if (isViewerVisible) {
            return TrackConstants.VIEWER_HIDE_ICON;
        } else {
            return TrackConstants.VIEWER_SHOW_ICON;
        }
    }

    /**
     * Indicate that the track's information cannot be resolved.
     *
     * @param erroneous true if the data is erroneous, false otherwise.
     */
    void setErroneous(final boolean erroneous) {
        trackModel.setErroneous(erroneous);
        trackPainter.setTrackModel(trackModel);
    }

    /**
     * Sets the state of the track model.
     *
     * @param state the new state to set.
     */
    private void setState(final TrackState state) {
        trackModel.setState(state);
        trackPainter.setTrackModel(trackModel);
    }

    /**
     * @return True if the track is selected, false otherwise.
     */
    public boolean isSelected() {
        return trackModel.isSelected();
    }

    /**
     * @return True if the track is locked, false otherwise.
     */
    public boolean isLocked() {
        return trackModel.isLocked();
    }

    /**
     * Set if the track carriage can be moved.
     *
     * @param lock true if the carriage is locked, false otherwise.
     */
    protected void setLocked(final boolean lock) {
        trackModel.setLocked(lock);
        if (lock) {
            lockUnlockButton.setIcon(TrackConstants.LOCK_ICON);
        } else {
            lockUnlockButton.setIcon(TrackConstants.UNLOCK_ICON);
        }
    }

    /**
     * @return Offset in milliseconds.
     */
    public long getOffset() {
        return trackModel.getOffset();
    }

    /**
     * @return Returns the duration of the track in milliseconds. Does not take
     * into account any offsets.
     */
    public long getDuration() {
        return trackModel.getDuration();
    }

    /**
     * @return View used by the controller
     */
    public JComponent getView() {
        return view;
    }

    /**
     * @return a clone of the track model used by the controller
     */
    TrackModel getTrackModel() {
        return trackModel.copy();
    }

    void deselect() {
        trackModel.setSelected(false);
        trackPainter.setTrackModel(trackModel);
    }

    /**
     * When the viewer tells us that the state of the project should change,
     * tell Datavyu to update the projectChanged state.
     */
    @Override
    public void notifyStateChanged(final String propertyChanged, final String newValue) {
        if (propertyChanged != null) {

            // Determine if we can handle the requested change
            boolean handled = false;
            String property = propertyChanged.toLowerCase();

            // FIXME empty property names are not allowed because we can't
            // create nameless variables. null properties are allowed but used
            // to represent multiple property changes.
            if (property.equals("")) {
                handled = true;
            }

            // FIXME this is hackish and it couples the plugins directly to
            // how this method works. Maybe a new data controller interface
            // method is needed. No other plugin developers are aware that
            // we can change the duration of a media file.
            if (property.equals("duration")) {
                handled = true;

                Long val = null;

                try {
                    val = Long.parseLong(newValue);
                } catch (NumberFormatException ex) {
                    logger.error("Error in format of long value: " + newValue);
                    handled = false;
                }

                if (val != null) {
                    trackModel.setDuration(val);
                    view.repaint();
                    Datavyu.getVideoController().updateMaxViewerDuration();
                    Datavyu.getVideoController().getMixerController().zoomToFit();
                }
            }

            // FIXME this is not an error, property change listeners should just
            // ignore properties they are not interested in.
            if (!handled) {

                // We couldn't find a way to handle the change- report this.
                logger.error("Unhandled property change: notified update of "
                        + propertyChanged + " to " + newValue);
            }
        }

        // FIXME move interface method into project controller?
        Datavyu.getProjectController().projectChanged();
    }

    void bindTrackActions(final CustomActions actions) {
        Runnable edtTask = new Runnable() {
            @Override
            public void run() {


                String cons = "w " + TrackConstants.ACTION_BUTTON_WIDTH + "!, h " + TrackConstants.ACTION_BUTTON_HEIGHT + "!";

                if (actions.getActionButton1() != null) {
                    header.add(actions.getActionButton1(),
                            cons + ", cell 2 2");
                }

                if (actions.getActionButton2() != null) {
                    header.add(actions.getActionButton2(),
                            cons + ", cell 3 2");
                }

                if (actions.getActionButton3() != null) {
                    header.add(actions.getActionButton3(),
                            cons + ", cell 4 2");
                }

                header.validate();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            edtTask.run();
        } else {
            SwingUtilities.invokeLater(edtTask);
        }
    }

    /**
     * Deselects the track if it is selected.
     */
    private void deselectTrack() {

        if (trackModel.isSelected()) {
            trackModel.setSelected(false);
            trackPainter.setTrackModel(trackModel);
            fireCarriageSelectionChangeEvent(false);
        }
    }

    /**
     * Selects the track if it isn't already selected.
     */
    private void selectTrack() {
        if (!trackModel.isSelected()) {
            trackModel.setSelected(true);
            trackPainter.setTrackModel(trackModel);
            fireCarriageSelectionChangeEvent(false);
        }
    }

    /**
     * Handles the event for locking and unlocking the track's movement.
     *
     * @param actionEvent event to handle.
     */
    private void handleLockUnlockButtonEvent(final ActionEvent actionEvent) {
        boolean isLocked = trackModel.isLocked();
        isLocked ^= true;
        trackModel.setLocked(isLocked);

        setLocked(isLocked);

        fireLockStateChangedEvent();
    }

    /**
     * Handles the event for removing a track with the rubbish bin button.
     *
     * @param actionEvent The event to handle.
     */
    private void handleDeleteButtonEvent(final ActionEvent actionEvent) {
        Datavyu.getVideoController().shutdown(trackModel.getIdentifier());
    }

    /**
     * Handles the event for hiding/showing a data viewer with the eye button.
     *
     * @param actionEvent The event to handle.
     */
    private void handleVisibleButtonEvent(final ActionEvent actionEvent) {
        isViewerVisible = !isViewerVisible;

        Datavyu.getVideoController().setStreamViewerVisibility(trackModel
                .getIdentifier(), isViewerVisible);

        visibleButton.setIcon(getVisibleButtonIcon());
    }

    /**
     * Register a mouse listener.
     *
     * @param listener listener to register.
     */
    public void addMouseListener(final MouseListener listener) {
        synchronized (this) {
            view.addMouseListener(listener);
        }
    }

    /**
     * Remove the mouse listener.
     *
     * @param listener listener to remove.
     */
    public void removeMouseListener(final MouseListener listener) {
        synchronized (this) {
            view.removeMouseListener(listener);
        }
    }

    /**
     * Register the listener to be notified of carriage events.
     *
     * @param listener listener to register.
     */
    void addCarriageEventListener(final CarriageEventListener listener) {
        synchronized (this) {
            listenerList.add(CarriageEventListener.class, listener);
        }
    }

    /**
     * Remove the listener from being notified of carriage events.
     *
     * @param listener listener to remove.
     */
    void removeCarriageEventListener(final CarriageEventListener listener) {
        synchronized (this) {
            listenerList.remove(CarriageEventListener.class, listener);
        }
    }

    /**
     * Used to inform listeners about a new carriage event
     *
     * @param newOffset the new offset to inform listeners about
     * @param time the time of the mouse when the new offset is triggered
     * @param hasModifiers true if modifiers were held down, false otherwise
     */
    private void fireCarriageOffsetChangeEvent(final long newOffset, final long time,
                                               final boolean hasModifiers) {
        synchronized (this) {
            final CarriageEvent e = new CarriageEvent(this, trackModel.getIdentifier(), newOffset, trackModel.getDuration(), time, EventType.OFFSET_CHANGE,
                    hasModifiers);
            final Object[] listeners = listenerList.getListenerList();
            // The listener list contains the listening class and then the listener instance
            for (int iListener = 0; iListener < listeners.length; iListener += 2) {
                if (listeners[iListener] == CarriageEventListener.class) {
                    ((CarriageEventListener) listeners[iListener + 1]).offsetChanged(e);
                }
            }
        }
    }

    /**
     * Used to inform listeners about track selection event.
     *
     * @param hasModifiers true if modifiers were held down, false otherwise.
     */
    private void fireCarriageSelectionChangeEvent(final boolean hasModifiers) {
        synchronized (this) {
            final CarriageEvent e = new CarriageEvent(this, trackModel.getIdentifier(),
                    trackModel.getOffset(), trackModel.getDuration(), 0, EventType.CARRIAGE_SELECTION,
                    hasModifiers);
            final Object[] listeners = listenerList.getListenerList();
            // The listener list contains the listening class and then the listener instance
            for (int iListener = 0; iListener < listeners.length; iListener += 2) {
                if (listeners[iListener] == CarriageEventListener.class) {
                    ((CarriageEventListener) listeners[iListener + 1]).selectionChanged(e);
                }
            }
        }
    }

    /**
     * Used to inform listeners about lock state change event.
     */
    private void fireLockStateChangedEvent() {
        synchronized (this) {
            final CarriageEvent carriageEvent = new CarriageEvent(this, trackModel.getIdentifier(),
                    trackModel.getOffset(), trackModel.getDuration(), 0,
                    EventType.CARRIAGE_LOCK, false);
            final Object[] listeners = listenerList.getListenerList();
            // The listener list contains the listening class and then the listener instance
            for (int iListener = 0; iListener < listeners.length; iListener += 2) {
                if (listeners[iListener] == CarriageEventListener.class) {
                    ((CarriageEventListener) listeners[iListener + 1]).lockStateChanged(carriageEvent);
                }
            }
        }
    }

    @Override
    public void propertyChange(final PropertyChangeEvent event) {
        if (event.getSource() == mixerModel.getViewportModel()) {
            view.repaint();
        }
    }

    public void attachAsWindowListener() {
        Datavyu.getVideoController().bindWindowListenerToStreamViewer(
                trackModel.getIdentifier(), new WindowAdapter() {

            @Override
            public void windowClosing(final WindowEvent e) {
                isViewerVisible = false;
                visibleButton.setIcon(getVisibleButtonIcon());
            }

        });

    }

    /**
     * Inner listener used to handle mouse events.
     */
    private class TrackPainterListener extends MouseInputAdapter {

        /**
         * Mouse cursor when hovering over a track that can be moved.
         */
        private final Cursor moveableTrackHoverCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        /**
         * Default mouse cursor.
         */
        private final Cursor defaultCursor = Cursor.getDefaultCursor();
        /**
         * Initial offset value.
         */
        private long offsetInit;
        /**
         * Is the mouse in the carriage.
         */
        private boolean inCarriage;
        /**
         * Whether the track was selected when the mouse was first pressed.
         */
        private boolean wasTrackSelected;
        /**
         * Initial x-coord position.
         */
        private int xInit;
        /**
         * Initial track state.
         */
        private TrackState initialState;
        private ViewportState viewport;

        @Override
        public void mouseEntered(final MouseEvent e) {
            updateCursor(e);
        }

        @Override
        public void mouseClicked(final MouseEvent e) {

            if (trackPainter.getCarriagePolygon().contains(e.getPoint())
                    && wasTrackSelected) {
                deselectTrack();
            }
        }

        @Override
        public void mouseMoved(final MouseEvent e) {
            updateCursor(e);
        }

        private void updateCursor(final MouseEvent e) {
            final boolean isHovering = trackPainter.getCarriagePolygon()
                    .contains(e.getPoint());
            trackPainter.setCursor((!trackModel.isLocked() && isHovering)
                    ? moveableTrackHoverCursor : defaultCursor);
        }

        @Override
        public void mousePressed(final MouseEvent e) {
            viewport = mixerModel.getViewportModel().getViewport();
            wasTrackSelected = trackModel.isSelected();

            if (trackPainter.getCarriagePolygon().contains(e.getPoint())) {
                inCarriage = true;
                xInit = e.getX();
                offsetInit = trackModel.getOffset();
                selectTrack();
                initialState = trackModel.getState();
                handleOffsetChanges(e);
            }
        }

        @Override
        public void mouseDragged(final MouseEvent e) {

            if (trackModel.isLocked()) {
                return;
            }

            handleOffsetChanges(e);
        }

        private void handleOffsetChanges(final MouseEvent e) {
            final boolean hasModifiers = e.isAltDown() || e.isAltGraphDown()
                    || e.isControlDown() || e.isMetaDown() || e.isShiftDown();

            if (inCarriage) {
                final int xNet = e.getX() - xInit;
                final double newOffset = viewport.computeTimeFromXOffset(xNet)
                        + offsetInit;
                final long temporalPosition = viewport.computeTimeFromXOffset(
                        e.getX()) + viewport.getViewStart();

                fireCarriageOffsetChangeEvent((long) newOffset, temporalPosition, hasModifiers);
            }
        }

        @Override
        public void mouseReleased(final MouseEvent e) {
            inCarriage = false;

            final Component source = (Component) e.getSource();
            source.setCursor(defaultCursor);
            setState(initialState);
        }
    }

}

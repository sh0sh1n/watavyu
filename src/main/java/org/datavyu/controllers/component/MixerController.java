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
import org.datavyu.Datavyu;
import org.datavyu.event.component.*;
import org.datavyu.event.component.TracksControllerEvent.EventType;
import org.datavyu.models.Identifier;
import org.datavyu.models.component.*;
import org.datavyu.plugins.CustomActions;
import org.datavyu.views.VideoController;
import org.datavyu.views.component.TrackPainter;

import javax.swing.*;
import javax.swing.Box.Filler;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.EventObject;
import java.util.concurrent.TimeUnit;


/**
 * This class manages tracks information
 */
public final class MixerController implements PropertyChangeListener,
        CarriageEventListener, AdjustmentListener, TimescaleListener {

    public static final long DEFAULT_DURATION = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);

    private static final double DEFAULT_ZOOM = 0.0;

    private final int V_SCROLL_WIDTH = 17;

    private static final int H_SCROLL_HEIGHT = 17;

    private static final int EDGE_PADDING = 5;

    private static final int MIXER_MIN_WIDTH = 785;

    private static final int FILLER_DEPTH_ORDER = 0;

    private static final int TIME_SCALE_DEPTH_ORDER = 5;

    private static final int TRACKS_DEPTH_ORDER = 10;

    private static final int NEEDLE_DEPTH_ORDER = 30;

    private static final int TRACKS_SCROLL_BAR_DEPTH_ORDER = 60;

    private final int TRACKS_SCROLL_BAR_RANGE = 1000000;

    /** Zoom icon */
    private final ImageIcon zoomIcon = new ImageIcon(getClass().getResource("/icons/magnifier.png"));

    /** Master mixer to listen to */
    private final MixerModel mixerModel;

    /** Viewport model */
    private final ViewportModel viewportModel;

    /** Tracks panel */
    private JPanel tracksPanel;

    /** Scroll pane with track information */
    private JScrollPane tracksScrollPane;

    /** Zoom value in (0, 1.0) where increasing values represent "zooming in" */
    private double zoomValue = DEFAULT_ZOOM;

    /** Listeners for track controller events */
    private EventListenerList eventListenerList;

    /** Controller managing the time scale */
    private TimescaleController timescaleController;

    /** Controller managing the timing needle */
    private NeedleController needleController;

    /** Controller managing tracks */
    private TracksEditorController tracksEditorController;

    /** Runs after the lock toggle for all tracks changes them. */
    private Runnable locksChanged = () -> { };

    /** Button for locking and unlocking all tracks */
    private JToggleButton lockToggle;

    /** Tracks horizontal scroll bar */
    private JScrollBar tracksScrollBar;

    /** Do we update the tracks scroll bar? */
    private boolean isUpdatingTracksScrollBar = false;

    /** Zoom slider */
    private JSlider zoomSlide;

    /** Update the zoom slider? */
    private boolean isUpdatingZoomSlide = false;

    /** Enable highlighting */
    private JButton enableHighlight;

    /** Enable highlighting and focus */
    private JButton enableHighlightAndFocus;

    /**
     * Create a new MixerController.
     */
    public MixerController() {
        mixerModel = new MixerModelImpl();

        viewportModel = mixerModel.getViewportModel();
        viewportModel.addPropertyChangeListener(this);

        runInEDT(new Runnable() {
            @Override
            public void run() {
                initView();
            }
        });
    }

    private static void runInEDT(final Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void initView() {
        eventListenerList = new EventListenerList();

        // Set up the root panel
        tracksPanel = new JPanel();
        tracksPanel.setLayout(new MigLayout("ins 0","[left|left|left]rel push[right|right] [left|left]", ""));
        tracksPanel.setBackground(Color.WHITE);

        // Menu buttons
        lockToggle = new JToggleButton("Lock all");
        lockToggle.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                lockToggleHandler(e);
            }
        });
        lockToggle.setName("lockToggleButton");

        final boolean highlightAndFocusByDefault = VideoController.HIGHLIGHT_AND_FOCUS_BY_DEFAULT;
        enableHighlight = new JButton(highlightAndFocusByDefault ? "Disable Cell Highlighting" : "Enable Cell Highlighting");
        enableHighlight.setEnabled(!highlightAndFocusByDefault);
        enableHighlight.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                enableHighlightHandler(e);
            }
        });
        enableHighlight.setName("enableHighlightButton");

        enableHighlightAndFocus = new JButton(
                highlightAndFocusByDefault ? "Disable Highlight and Focus" : "Enable Highlight and Focus");
        enableHighlightAndFocus.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                Datavyu.getView().toggleHighlightAndFocusMode();
            }
        });
        enableHighlightAndFocus.setName("enableHighlightAndFocusButton");

        zoomSlide = new JSlider(JSlider.HORIZONTAL, 1, 1000, 1);
        zoomSlide.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(final ChangeEvent e) {

                if (!isUpdatingZoomSlide && zoomSlide.getValueIsAdjusting()) {
                    try {
                        isUpdatingZoomSlide = true;
                        zoomValue = (double) (zoomSlide.getValue() - zoomSlide.getMinimum())
                                           / (zoomSlide.getMaximum() - zoomSlide.getMinimum() + 1);
                        viewportModel.setViewportZoom(zoomValue, needleController.getNeedleModel().getCurrentTime());
                    } finally {
                        isUpdatingZoomSlide = false;
                    }
                }
            }
        });
        zoomSlide.setName("zoomSlider");
        zoomSlide.setBackground(tracksPanel.getBackground());

        JButton zoomToFitButton = new JButton("", zoomIcon);
        zoomToFitButton.setToolTipText("Zoom to fit all tracks");
        zoomToFitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                zoomToFit();
            }
        });
        zoomToFitButton.setName("zoomToFitButton");

        tracksPanel.add(lockToggle);
        tracksPanel.add(zoomToFitButton);
        tracksPanel.add(zoomSlide, "wrap");
        tracksPanel.add(enableHighlight);
        tracksPanel.add(enableHighlightAndFocus, "wrap");

        timescaleController = new TimescaleController(mixerModel);
        timescaleController.addTimescaleEventListener(this);
        needleController = new NeedleController(this, mixerModel);
        tracksEditorController = new TracksEditorController(mixerModel);

        needleController.setTimescaleTransitionHeight(
                timescaleController.getTimescaleModel()
                        .getZoomWindowToTrackTransitionHeight());
        needleController.setZoomIndicatorHeight(
                timescaleController.getTimescaleModel()
                        .getZoomWindowIndicatorHeight());

        // Set up the layered pane
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(new MigLayout("fillx, ins 0"));

        final int layeredPaneHeight = 272;
        final int timescaleViewHeight = timescaleController.getTimescaleModel().getHeight();

        final int needleHeadHeight = (int) Math.ceil(NeedleConstants.NEEDLE_HEAD_HEIGHT);
        final int tracksScrollPaneY = needleHeadHeight + 1;
        final int timescaleViewY = layeredPaneHeight - H_SCROLL_HEIGHT - timescaleViewHeight;
        final int tracksScrollPaneHeight = timescaleViewY - tracksScrollPaneY;
        final int tracksScrollBarY = timescaleViewY + timescaleViewHeight;
        final int needleHeight = (timescaleViewY
                + timescaleViewHeight
                - timescaleController.getTimescaleModel()
                .getZoomWindowIndicatorHeight()
                - timescaleController.getTimescaleModel()
                .getZoomWindowToTrackTransitionHeight() + 1);

        // Set up filler component responsible for horizontal resizing of the layout.
        {
            // Null args; let layout manager handle sizes.
            Box.Filler filler = new Filler(null, null, null);
            filler.setName("Filler");
            filler.addComponentListener(new SizeHandler());


            // TODO Could probably use this same component to handle vertical resizing...

            layeredPane.setLayer(filler, FILLER_DEPTH_ORDER);
            String layout = "id filler, h 0!, grow 100 0, wmin " + MIXER_MIN_WIDTH + ", cell 0 0 ";
            layeredPane.add(filler, layout, FILLER_DEPTH_ORDER);
        }

        // Set up the timescale layout
        {
            JComponent timescaleView = timescaleController.getTimescaleComponent();


            // Calculate padding from the right
            int rightPad = TrackConstants.TIMELINE_END_PADDING + V_SCROLL_WIDTH + EDGE_PADDING;


            // Must call setLayer first.
            layeredPane.setLayer(timescaleView, TIME_SCALE_DEPTH_ORDER);
            String layout = "id tscale, pos " + TimescaleConstants.XPOS_ABS + " " + timescaleViewY + " " + ("(filler.w-" + rightPad + ")") + " (tscale.y+" + timescaleViewHeight + ")";
            layeredPane.add(timescaleView, layout, TIME_SCALE_DEPTH_ORDER);
        }

        // Set up the scroll pane's layout.
        {
            tracksScrollPane = new JScrollPane(tracksEditorController.getView());
            tracksScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
            tracksScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            tracksScrollPane.setBorder(BorderFactory.createEmptyBorder());
            tracksScrollPane.setName("jScrollPane");



            layeredPane.setLayer(tracksScrollPane, TRACKS_DEPTH_ORDER);
            String layout = "pos 0 " + tracksScrollPaneY + " " + ("(filler.w-" + EDGE_PADDING + ")") + " n, h " + tracksScrollPaneHeight + "!";
            layeredPane.add(tracksScrollPane, layout, TRACKS_DEPTH_ORDER);
        }

        // Set up the timing needle's layout
        {
            JComponent needleView = needleController.getNeedleComponent();


            int x = (int) (TrackConstants.HEADER_WIDTH
                    - NeedleConstants.NEEDLE_HEAD_WIDTH
                    + NeedleConstants.NEEDLE_WIDTH);

            // Padding from the right
            int rightPad = EDGE_PADDING + V_SCROLL_WIDTH - 1;



            layeredPane.setLayer(needleView, NEEDLE_DEPTH_ORDER);
            String layout = "pos " + x + " 0 " + ("(filler.w-" + rightPad + ")") + " n, h " + (needleHeight
                                    + timescaleController.getTimescaleModel()
                                    .getZoomWindowToTrackTransitionHeight()
                                    + timescaleController.getTimescaleModel()
                                    .getZoomWindowIndicatorHeight() - 1) + "::";
            layeredPane.add(needleView, layout, NEEDLE_DEPTH_ORDER);
        }

        // Set up the tracks horizontal scroll bar
        {
            tracksScrollBar = new JScrollBar(Adjustable.HORIZONTAL);
            tracksScrollBar.setValues(0, TRACKS_SCROLL_BAR_RANGE, 0, TRACKS_SCROLL_BAR_RANGE);
            tracksScrollBar.setUnitIncrement(TRACKS_SCROLL_BAR_RANGE / 20);
            tracksScrollBar.setBlockIncrement(TRACKS_SCROLL_BAR_RANGE / 2);
            tracksScrollBar.addAdjustmentListener(this);
            tracksScrollBar.setValueIsAdjusting(false);
            tracksScrollBar.setVisible(false);
            tracksScrollBar.setName("horizontalScrollBar");


            int rightPad = TrackConstants.TIMELINE_END_PADDING + V_SCROLL_WIDTH + EDGE_PADDING;


            layeredPane.setLayer(tracksScrollBar, TRACKS_SCROLL_BAR_DEPTH_ORDER);
            String layout = "pos " + TimescaleConstants.XPOS_ABS + " " + tracksScrollBarY + " " + ("(filler.w-" + rightPad + ")") + " n, h " + H_SCROLL_HEIGHT + "::";
            layeredPane.add(tracksScrollBar, layout, TRACKS_SCROLL_BAR_DEPTH_ORDER);
        }

        {


            String layout = "growx, span 6, w " + MIXER_MIN_WIDTH + "::, h " + layeredPaneHeight + "::, wrap";
            tracksPanel.add(layeredPane, layout);
        }

        tracksPanel.validate();
    }

    /**
     * @return the panel containing the tracks interface.
     */
    public JPanel getTracksPanel() {
        return tracksPanel;
    }

    /**
     * Add a new track to the interface.
     *
     * @param id           Identifier of the track.
     * @param icon         Icon associated with the track.
     * @param mediaPath    Absolute path to the media file.
     * @param duration     The total duration of the track in milliseconds.
     * @param offset       The amount of playback offset in milliseconds.
     * @param trackPainter The track painter to use.
     * @param frameRate    Stream's Frame Rate.
     */
    public void addNewTrack(final Identifier id, final ImageIcon icon,
                            final File mediaPath, final long duration,
                            final long offset, final TrackPainter trackPainter, final float frameRate) {

        // Check if the scale needs to be updated
        final long trackEnd = duration + offset;
        final ViewportState viewport = viewportModel.getViewport();

        if ((trackEnd > viewport.getMaxEnd()) || ((tracksEditorController.numberOfTracks() == 0) && (trackEnd > 0))) {
            viewportModel.setViewportMaxEnd(trackEnd, true);
        }

        tracksEditorController.addNewTrack(id, icon, mediaPath, duration, offset, this, trackPainter, frameRate);
        tracksScrollPane.validate();

        updateGlobalLockToggle();
    }

    /**
     * Bind track actions to a data viewer.
     *
     * @param trackId Identifier of the track
     * @param actions Actions to bind with
     */
    public void bindTrackActions(final Identifier trackId, final CustomActions actions) {
        if (actions == null) {
            return;
        }
        runInEDT(new Runnable() {
            @Override
            public void run() {
                tracksEditorController.bindTrackActions(trackId, actions);
            }
        });
    }

    /**
     * Sets whether a track can be moved.
     *
     * @param trackId Track identifier.
     * @param lock True if track movement is locked, false otherwise.
     */
    /**
     * Sets what runs after the lock toggle for all tracks changes them.
     *
     * @param listener what to run, on the EDT
     */
    public void setLocksChangedListener(final Runnable listener) {
        locksChanged = listener;
    }

    public void setTrackLocked(final Identifier trackId, final boolean lock) {
        runInEDT(() -> tracksEditorController.setMovementLock(trackId, lock));
    }

    /**
     * Sets whether a track can be moved, for project files that identify tracks by media path. If several tracks
     * show the same media, only the first is changed.
     *
     * @param mediaPath Absolute path to the media file.
     * @param lock      True if track movement is locked, false otherwise.
     */
    @Deprecated
    public void setTrackLocked(final String mediaPath, final boolean lock) {
        runInEDT(() -> tracksEditorController.setMovementLock(mediaPath, lock));
    }

    /**
     * Zooms out to show the whole timeline and moves the needle to its start.
     */
    public void zoomToFit() {
        viewportModel.setViewportWindow(0, viewportModel.getViewport().getMaxEnd());
        needleController.setCurrentTime(0);
    }

    /**
     * Remove from track panel.
     *
     * @param trackId identifier of the track to remove.
     */
    public void deregisterTrack(final Identifier trackId) {
        tracksEditorController.removeTrack(trackId, this);

        // Update tracks panel display
        tracksScrollPane.validate();

        updateGlobalLockToggle();
    }

    /**
     * Removes all track components from this controller and resets components.
     */
    public void removeAll() {
        tracksEditorController.removeAllTracks();

        viewportModel.resetViewport();
        needleController.setCurrentTime(0);

        tracksScrollPane.validate();

        tracksPanel.validate();
        tracksPanel.repaint();

        updateGlobalLockToggle();
    }

    public TrackModel getTrackModel(final Identifier id) {
        return tracksEditorController.getTrackModel(id);
    }

    public MixerModel getMixerModel() {
        return mixerModel;
    }

    /**
     * @return NeedleController.
     */
    public NeedleController getNeedleController() {
        return needleController;
    }

    /**
     * @return TimescaleController.
     */
    public TimescaleController getTimescaleController() {
        return timescaleController;
    }

    /**
     * @return TracksEditorController.
     */
    public TracksEditorController getTracksEditorController() {
        return tracksEditorController;
    }

    private void updateZoomSlide(final ViewportState viewport) {
        assert SwingUtilities.isEventDispatchThread();

        if (isUpdatingZoomSlide) {
            return;
        }

        try {
            isUpdatingZoomSlide = true;

            zoomSlide.setValue((int) Math.round(
                    (viewport.getZoomLevel()
                            * (zoomSlide.getMaximum() - zoomSlide.getMinimum()
                            + 1)) + zoomSlide.getMinimum()));
        } finally {
            isUpdatingZoomSlide = false;
        }
    }

    /**
     * Update scroll bar values.
     */
    private void updateTracksScrollBar(final ViewportState viewport) {
        assert SwingUtilities.isEventDispatchThread();

        if (isUpdatingTracksScrollBar) {
            return;
        }

        try {
            isUpdatingTracksScrollBar = true;

            final int startValue = (int) Math.round(
                    (double) viewport.getViewStart() * TRACKS_SCROLL_BAR_RANGE
                            / viewport.getMaxEnd());
            final int extentValue = (int) Math.round(
                    (double) (viewport.getViewDuration())
                            * TRACKS_SCROLL_BAR_RANGE / viewport.getMaxEnd());

            tracksScrollBar.setValues(startValue, extentValue, 0,
                    TRACKS_SCROLL_BAR_RANGE);
            tracksScrollBar.setUnitIncrement(extentValue / 20);
            tracksScrollBar.setBlockIncrement(extentValue / 2);
            tracksScrollBar.setVisible((viewport.getViewDuration())
                    < viewport.getMaxEnd());
        } finally {
            isUpdatingTracksScrollBar = false;
            tracksPanel.validate();
        }
    }

    /**
     * Enables/Disables cell highlighting.
     *
     * @param e The event that triggered this action.
     */
    private void enableHighlightHandler(final ActionEvent e) {
        Datavyu.getVideoController().toggleCellHighlighting();

        if (!Datavyu.getVideoController().getCellHighlighting()) {
            enableHighlight.setText("Enable Cell Highlighting");
        } else {
            enableHighlight.setText("Disable Cell Highlighting");
        }

        Datavyu.getProjectController().getSpreadSheetPanel().redrawCells();
    }

    public void enableHighlightAndFocusHandler(final ActionEvent e) {
        Datavyu.getVideoController().toggleCellHighlightingAutoFocus();

        if (!Datavyu.getVideoController().getCellHighlightAndFocus()) {
            enableHighlightAndFocus.setText("Enable Highlight and Focus");
            if (Datavyu.getVideoController().getCellHighlighting()) {
                enableHighlightHandler(null);
            }
        } else {
            enableHighlightAndFocus.setText("Disable Highlight and Focus");
            if (!Datavyu.getVideoController().getCellHighlighting()) {
                enableHighlightHandler(null);
            }
        }

        enableHighlight.setEnabled(!Datavyu.getVideoController().getCellHighlightAndFocus());
        Datavyu.getProjectController().getSpreadSheetPanel().redrawCells();
    }

    /**
     * Handles the event for toggling movement of tracks on and off.
     *
     * @param e the event to handle
     */
    private void lockToggleHandler(final ActionEvent e) {
        JToggleButton toggle = (JToggleButton) e.getSource();
        tracksEditorController.setLockedState(toggle.isSelected());
        updateGlobalLockToggle();
        locksChanged.run();
    }

    /**
     * Handles the event for scrolling the tracks interface horizontally.
     *
     * @param e the event to handle
     */
    @Override
    public void adjustmentValueChanged(final AdjustmentEvent e) {

        if (isUpdatingTracksScrollBar) {
            return;
        }

        final ViewportState viewport = viewportModel.getViewport();

        final int startValue = tracksScrollBar.getValue();

        assert tracksScrollBar.getMinimum() == 0;

        final long newWindowStart = (long) Math.round((double) startValue
                / tracksScrollBar.getMaximum() * viewport.getMaxEnd());
        final long newWindowEnd = newWindowStart + viewport.getViewDuration()
                - 1;

        viewportModel.setViewportWindow(newWindowStart, newWindowEnd);

        tracksPanel.repaint();
    }

    /**
     * TrackPainter recorded a change in the track's offset using the mouse.
     *
     * @param e the event to handle
     */
    @Override
    public void offsetChanged(final CarriageEvent e) {
        final boolean wasOffsetChanged = tracksEditorController.setTrackOffset(e.getTrackId(), e.getOffset());
        final CarriageEvent newEvent;

        if (wasOffsetChanged) {
            final long newOffset = tracksEditorController.getTrackModel(
                    e.getTrackId()).getOffset();
            newEvent = new CarriageEvent(e.getSource(), e.getTrackId(),
                    newOffset, e.getDuration(),
                    e.getTime(), e.getEventType(),
                    e.hasModifiers());
        } else {
            newEvent = e;
        }

        fireTracksControllerEvent(EventType.CARRIAGE_EVENT, newEvent);
        tracksPanel.invalidate();
        tracksPanel.repaint();
    }

    /**
     * Track lock state changed.
     *
     * @param e the event to handle
     */
    @Override
    public void lockStateChanged(final CarriageEvent e) {
        fireTracksControllerEvent(EventType.CARRIAGE_EVENT, e);
        updateGlobalLockToggle();
    }

    /**
     * A track's selection state was changed.
     *
     * @param e the event to handle
     */
    @Override
    public void selectionChanged(final CarriageEvent e) {
        // Selection has no effect on the mixer's own controls
    }

    @Override

    public void jumpToTime(final TimescaleEvent e) {
        fireTracksControllerEvent(EventType.TIMESCALE_EVENT, e);
    }

    /**
     * Register listeners who are interested in events from this class.
     *
     * @param listener the listener to register
     */
    public void addTracksControllerListener(final TracksControllerListener listener) {
        synchronized (this) {
            eventListenerList.add(TracksControllerListener.class, listener);
        }
    }

    private void updateGlobalLockToggle() {

        if (tracksEditorController.isAnyTrackUnlocked()) {
            lockToggle.setSelected(false);
            lockToggle.setText("Lock all");
        } else {
            lockToggle.setSelected(true);
            lockToggle.setText("Unlock all");
        }
    }

    /**
     * Used to fire a new event informing listeners about new child component
     * events.
     *
     * @param tracksEvent The event to handle
     * @param eventObject The event object to repackage
     */
    private void fireTracksControllerEvent(final EventType tracksEvent, final EventObject eventObject) {
        TracksControllerEvent e = new TracksControllerEvent(this, tracksEvent, eventObject);
        Object[] listeners = eventListenerList.getListenerList();
        synchronized (this) {
            // The listener list contains the listening class and then the listener instance
            for (int iListener = 0; iListener < listeners.length; iListener += 2) {
                if (listeners[iListener] == TracksControllerListener.class) {
                    ((TracksControllerListener) listeners[iListener + 1])
                            .tracksControllerChanged(e);
                }
            }
        }
    }

    private void handleViewportChanged(final ViewportState newViewport) {
        runInEDT(new Runnable() {
            @Override
            public void run() {
                updateZoomSlide(newViewport);
                updateTracksScrollBar(newViewport);
                tracksScrollPane.repaint();
            }
        });
    }

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {

        if (evt.getSource() == mixerModel.getViewportModel()) {
            final ViewportState newViewport =
                    (evt.getNewValue() instanceof ViewportState)
                            ? (ViewportState) evt.getNewValue() : null;
            handleViewportChanged(newViewport);
        }
    }

    private void handleResize() {
        final ViewportState viewport = viewportModel.getViewport();

        if (Double.isNaN(viewport.getResolution())) {
            viewportModel.setViewport(viewport.getViewStart(),
                    viewport.getViewEnd(), viewport.getMaxEnd(),
                    timescaleController.getTimescaleComponent().getWidth());
        } else {
            viewportModel.resizeViewport(viewportModel.getViewport()
                    .getViewStart(), timescaleController.getTimescaleComponent().getWidth());
        }
    }

    /**
     * Handles component resizing
     */
    private final class SizeHandler extends ComponentAdapter {
        @Override
        public void componentResized(final ComponentEvent e) {
            handleResize();
        }
    }

}

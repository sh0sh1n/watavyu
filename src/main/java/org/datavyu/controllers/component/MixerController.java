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
import org.datavyu.event.component.TimescaleEvent;
import org.datavyu.event.component.TimescaleListener;
import org.datavyu.event.component.TracksControllerEvent;
import org.datavyu.event.component.TracksControllerEvent.EventType;
import org.datavyu.event.component.TracksControllerListener;
import org.datavyu.models.component.MixerModel;
import org.datavyu.models.component.MixerModelImpl;
import org.datavyu.models.component.NeedleConstants;
import org.datavyu.models.component.TimescaleConstants;
import org.datavyu.models.component.ViewportModel;
import org.datavyu.models.component.ViewportState;

import javax.swing.Box;
import javax.swing.Box.Filler;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EventObject;
import java.util.concurrent.TimeUnit;

/**
 * The video controller's timeline: a time ruler and a needle that can be dragged to seek. It always shows the whole
 * timeline, from zero to the end of the media. Media tracks are no longer shown here; media is in the spreadsheet's
 * media columns.
 */
public final class MixerController implements PropertyChangeListener, TimescaleListener {

    public static final long DEFAULT_DURATION = TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES);

    private static final int EDGE_PADDING = 5;

    private static final int MIXER_MIN_WIDTH = 785;

    private static final int FILLER_DEPTH_ORDER = 0;

    private static final int TIME_SCALE_DEPTH_ORDER = 5;

    private static final int NEEDLE_DEPTH_ORDER = 30;

    /** Master mixer to listen to */
    private final MixerModel mixerModel;

    /** Viewport model */
    private final ViewportModel viewportModel;

    /** The timeline's panel */
    private JPanel tracksPanel;

    /** Listeners for timeline events */
    private final EventListenerList eventListenerList = new EventListenerList();

    /** Controller managing the time scale */
    private TimescaleController timescaleController;

    /** Controller managing the timing needle */
    private NeedleController needleController;

    /**
     * Create a new MixerController.
     */
    public MixerController() {
        mixerModel = new MixerModelImpl();

        viewportModel = mixerModel.getViewportModel();
        viewportModel.addPropertyChangeListener(this);

        runInEDT(this::initView);
    }

    private static void runInEDT(final Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void initView() {
        tracksPanel = new JPanel(new MigLayout("ins 0, fillx"));
        tracksPanel.setBackground(java.awt.Color.WHITE);
        tracksPanel.setName("tracksPanel");

        timescaleController = new TimescaleController(mixerModel);
        timescaleController.addTimescaleEventListener(this);
        needleController = new NeedleController(this, mixerModel);

        needleController.setTimescaleTransitionHeight(
                timescaleController.getTimescaleModel().getZoomWindowToTrackTransitionHeight());
        needleController.setZoomIndicatorHeight(
                timescaleController.getTimescaleModel().getZoomWindowIndicatorHeight());

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(new MigLayout("fillx, ins 0"));

        final int timescaleViewHeight = timescaleController.getTimescaleModel().getHeight();
        final int needleHeadHeight = (int) Math.ceil(NeedleConstants.NEEDLE_HEAD_HEIGHT);
        final int timescaleViewY = needleHeadHeight + 1;
        final int layeredPaneHeight = timescaleViewY + timescaleViewHeight + EDGE_PADDING;
        final int needleHeight = timescaleViewY + timescaleViewHeight
                - timescaleController.getTimescaleModel().getZoomWindowIndicatorHeight()
                - timescaleController.getTimescaleModel().getZoomWindowToTrackTransitionHeight() + 1;

        // A filler resizes the layout horizontally.
        Box.Filler filler = new Filler(null, null, null);
        filler.setName("Filler");
        filler.addComponentListener(new SizeHandler());
        layeredPane.setLayer(filler, FILLER_DEPTH_ORDER);
        layeredPane.add(filler, "id filler, h 0!, grow 100 0, wmin " + MIXER_MIN_WIDTH + ", cell 0 0 ",
                FILLER_DEPTH_ORDER);

        int rightPad = TimescaleConstants.END_PADDING + EDGE_PADDING;

        JComponent timescaleView = timescaleController.getTimescaleComponent();
        layeredPane.setLayer(timescaleView, TIME_SCALE_DEPTH_ORDER);
        layeredPane.add(timescaleView, "id tscale, pos " + TimescaleConstants.XPOS_ABS + " " + timescaleViewY
                + " (filler.w-" + rightPad + ") (tscale.y+" + timescaleViewHeight + ")", TIME_SCALE_DEPTH_ORDER);

        JComponent needleView = needleController.getNeedleComponent();
        int needleX = (int) (TimescaleConstants.XPOS_ABS - 1 - NeedleConstants.NEEDLE_HEAD_WIDTH
                + NeedleConstants.NEEDLE_WIDTH);
        layeredPane.setLayer(needleView, NEEDLE_DEPTH_ORDER);
        layeredPane.add(needleView, "pos " + needleX + " 0 (filler.w-" + (EDGE_PADDING - 1) + ") n, h "
                + (needleHeight + timescaleController.getTimescaleModel().getZoomWindowToTrackTransitionHeight()
                + timescaleController.getTimescaleModel().getZoomWindowIndicatorHeight() - 1) + "::",
                NEEDLE_DEPTH_ORDER);

        tracksPanel.add(layeredPane, "growx, span 6, w " + MIXER_MIN_WIDTH + "::, h " + layeredPaneHeight + "::, wrap");
        tracksPanel.validate();
    }

    /**
     * @return the panel containing the timeline.
     */
    public JPanel getTracksPanel() {
        return tracksPanel;
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
     * Sets where the timeline ends, and shows all of it.
     *
     * @param end the end of the media, in milliseconds
     */
    public void setTimelineEnd(final long end) {
        viewportModel.setViewportMaxEnd(end, false);
        viewportModel.setViewportWindow(0, viewportModel.getViewport().getMaxEnd());
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

    private void fireTracksControllerEvent(final EventType tracksEvent, final EventObject eventObject) {
        TracksControllerEvent e = new TracksControllerEvent(this, tracksEvent, eventObject);
        Object[] listeners = eventListenerList.getListenerList();
        synchronized (this) {
            // The listener list contains the listening class and then the listener instance
            for (int iListener = 0; iListener < listeners.length; iListener += 2) {
                if (listeners[iListener] == TracksControllerListener.class) {
                    ((TracksControllerListener) listeners[iListener + 1]).tracksControllerChanged(e);
                }
            }
        }
    }

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
        if (evt.getSource() == mixerModel.getViewportModel() && evt.getNewValue() instanceof ViewportState) {
            runInEDT(() -> {
                if (tracksPanel != null) {
                    tracksPanel.repaint();
                }
            });
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

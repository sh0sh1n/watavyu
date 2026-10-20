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
package org.datavyu.views;

import org.datavyu.controllers.project.MediaPlayers;
import org.datavyu.util.Settings;
import org.datavyu.models.db.UserWarningException;
import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.controllers.CreateNewCellController;
import org.datavyu.controllers.SetNewCellStopTimeController;
import org.datavyu.controllers.SetSelectedCellStartTimeController;
import org.datavyu.controllers.SetSelectedCellStopTimeController;
import org.datavyu.controllers.component.MixerController;
import org.datavyu.event.component.TimescaleEvent;
import org.datavyu.event.component.TracksControllerEvent;
import org.datavyu.event.component.TracksControllerListener;
import org.datavyu.models.Identifier;
import org.datavyu.models.component.*;
import org.datavyu.plugins.StreamViewer;
import org.datavyu.plugins.javafx.JavaFxVideoPlugin;
import org.datavyu.util.*;
import org.datavyu.util.ClockTimer.ClockListener;
import org.datavyu.util.Resources;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;


/**
 * Video controller
 */
public final class VideoController extends DatavyuDialog
        implements ClockListener, TracksControllerListener, PropertyChangeListener {

    /** The logger for this class */
    private static Logger logger = LogManager.getLogger(VideoController.class);

    /** One second in milliseconds */
    private static final long MILLI_IN_SEC = 1000L;

    /** The 45x45 size for num pad keys */
    private static final int NUM_PAD_KEY_HEIGHT = 45;

    private static final int NUM_PAD_KEY_WIDTH = 45;

    private static final String NUM_PAD_KEY_SIZE = "w " + NUM_PAD_KEY_HEIGHT + "!, h " + NUM_PAD_KEY_WIDTH + "!";

    /** The 45x95 size for tall num pad keys (enter, PC plus) */
    private static final int TALL_NUM_PAD_KEY_HEIGHT = 95;

    private static final String TALL_NUM_PAD_KEY_SIZE = "span 1 2, w " + NUM_PAD_KEY_WIDTH + "!, h "
            + TALL_NUM_PAD_KEY_HEIGHT + "!";

    /** The 80x40 size for the text fields to the right of num pad */
    private static final int WIDE_TEXT_FIELD_WIDTH = 90;

    private static final int WIDE_TEXT_FIELD_HEIGHT = 45;

    private static final String WIDE_TEXT_FIELD_SIZE = "w " + WIDE_TEXT_FIELD_WIDTH + "!, h "
            + WIDE_TEXT_FIELD_HEIGHT + "!";

    private static final Font TEXT_FIELD_FONT = new Font("Arial", Font.PLAIN, 10);

    private static final Font TEXT_LABEL_FONT = new Font("Arial", Font.PLAIN, 10);

    /** Format for time */
    private static int timeStampFontSize = 15;

    /** Colours of the hours, minutes, seconds and milliseconds in the current time label. */
    private static final Color[] CLOCK_COLORS = {
        TimescaleConstants.HOURS_COLOR, TimescaleConstants.MINUTES_COLOR,
        TimescaleConstants.SECONDS_COLOR, TimescaleConstants.MILLISECONDS_COLOR
    };

    private static RateController shuttleRates = new RateController();

    /**
     * // TODO: What is this doing here?
     *
     * Visible?
     */
    private boolean visible;

    /** Determines whether or not the 'shift' key is being held */
    private boolean shiftMask = false;

    /** The set of streamViewers associated with this controller */
    private LinkedHashSet<StreamViewer> streamViewers = new LinkedHashSet<>();

    /** Clock timer */
    private final ClockTimer clockTimer = new ClockTimer();

    /** Is the tracks panel currently shown */
    private boolean tracksPanelVisible = true;

    /** The controller for manipulating tracks */
    private MixerController mixerController = new MixerController();

    /** Keeps the tracks and the spreadsheet's media columns in step; null until a spreadsheet is set. */
    private MediaPlayers mediaPlayers;

    /** Button to create a new cell */
    private JButton createNewCell;

    /** Button to create a new cell setting offset */
    private JButton createNewCellSettingOffset;

    /** */
    private JButton findButton;

    /** */
    private JTextField offsetTextField;

    /** */
    private JTextField onsetTextField;

    /** */
    private JButton jumpByButton;

    /** */
    private JTextField jumpByTextField;

    /** */
    private JTextField stepSizeTextField;

    /** */
    private JLabel stepSizeLabel = new JLabel("Frame Rate");

    /** */
    private JPanel stepSizePanel;

    /** */
    private JPanel gridButtonPanel;

    /** */
    private JLabel labelSpeed;

    /** */
    private JButton pauseButton;

    /** */
    private JButton playButton;

    /** */
    private JButton nineSetCellOffsetButton;

    /** */
    private JButton setCellOffsetButton;

    /** */
    private JButton setCellOnsetButton;

    /** */
    private JButton pointCellButton;

    /** */
    private JButton showTracksSmallButton;

    /** */
    private JButton shuttleBackButton;

    /** */
    private JButton shuttleForwardButton;

    /** */
    private JButton stopButton;

    /** */
    private JLabel timeStampLabel;

    /** */
    private JPanel tracksPanel;

    private Resources resourceMap;

    /**
     * Whether video controllers start in highlight and focus mode, which highlights the cell at the current time in
     * the selected column and focuses its first uncoded argument. The mode needs cell highlighting, so that starts on
     * with it.
     */
    public static final boolean HIGHLIGHT_AND_FOCUS_BY_DEFAULT = true;

    private boolean highlightCells = HIGHLIGHT_AND_FOCUS_BY_DEFAULT;

    private boolean highlightAndFocus = HIGHLIGHT_AND_FOCUS_BY_DEFAULT;

    private FrameRateController frameRateController = new FrameRateController();

    private int jumpDirection = -1;

    /**
     * Create a new VideoController.
     *
     * @param parent The parent of this form.
     * @param modal  Should the dialog be modal or not?
     */
    public VideoController(final Frame parent, final boolean modal) {
        super(parent, modal);

        clockTimer.registerListener(this);

        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        resourceMap = Resources.of(VideoController.class);


        initComponents();

        setResizable(false);

        final int defaultEndTime = (int) MixerController.DEFAULT_DURATION;

        clockTimer.setMinTime(0);
        clockTimer.setMaxTime(defaultEndTime);

        tracksPanel.add(mixerController.getTracksPanel(), "growx");
        mixerController.addTracksControllerListener(this);
        mixerController.getMixerModel().getViewportModel().addPropertyChangeListener(this);
        mixerController.getMixerModel().getNeedleModel().addPropertyChangeListener(this);

        showTracksPanel(tracksPanelVisible);

        visible = true;
    }

    /**
     * TODO: Put to some time utils?
     *
     * @param time
     * @return
     */
    public static String formatTime(final long time) {
        return Timestamp.format(time);
    }

    /**
     * @param time a time in milliseconds
     * @return the time as {@code HH:MM:SS:mmm} in HTML, each field in its timescale colour
     */
    static String formatTimeHtml(final long time) {
        String[] fields = Timestamp.format(time).split(":");
        StringBuilder html = new StringBuilder("<html>");
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                html.append(':');
            }
            html.append("<font color=\"").append(toRGBString(CLOCK_COLORS[i])).append("\">")
                .append(fields[i]).append("</font>");
        }
        return html.append("</html>").toString();
    }

    /**
     * TODO: Put to some color utils?
     *
     * @param color
     * @return
     */
    private static String toRGBString(final Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Opens a video file as a new track.
     *
     * @param videoFile Video file.
     * @return The identifier of the new track.
     */
    public void openVideo(final File videoFile) {
        if (mediaPlayers == null) {
            logger.error("No spreadsheet to add " + videoFile + " to");
            return;
        }
        try {
            mediaPlayers.addMediaFiles(java.util.List.of(videoFile), getCurrentTime());
        } catch (UserWarningException e) {
            logger.error("Unable to add " + videoFile, e);
            JOptionPane.showMessageDialog(Datavyu.getApplication().getMainFrame(), e.getMessage(),
                "Cannot Open Video", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Sets what keeps this controller's tracks and the spreadsheet's media columns in step.
     *
     * @param mediaPlayers the spreadsheet's media players
     */
    public void setMediaPlayers(final MediaPlayers mediaPlayers) {
        this.mediaPlayers = mediaPlayers;
    }

    /**
     * Opens a stream on a background thread, because the player blocks while its media
     * loads, then adds the track on the Swing EDT. A file that fails to open adds no track
     * and shows the player's error message.
     *
     * @param id Identifier for the new stream.
     * @param file File to open.
     * @param beforeAdd Runs on the EDT before the track is added, e.g. to load saved settings.
     * @param afterAdd Runs on the EDT after the track is added.
     */
    public void openStream(final Identifier id, final File file,
                           final Consumer<StreamViewer> beforeAdd, final Consumer<StreamViewer> afterAdd) {
        openStream(id, file, beforeAdd, afterAdd, () -> { });
    }

    /**
     * Opens a stream as {@link #openStream(Identifier, File, Consumer, Consumer)} does.
     *
     * @param failed Runs on the EDT if the file does not open, after the player's error message.
     */
    public void openStream(final Identifier id, final File file, final Consumer<StreamViewer> beforeAdd,
                           final Consumer<StreamViewer> afterAdd, final Runnable failed) {
        Thread opener = new Thread(() -> {
            final StreamViewer streamViewer;
            try {
                logger.debug("Open video " + file);
                streamViewer = JavaFxVideoPlugin.open(id, file, Datavyu.getApplication().getMainFrame(), false);
            } catch (Throwable t) {
                logger.error("Unable to open " + file, t);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(Datavyu.getApplication().getMainFrame(),
                            t.getMessage() != null ? t.getMessage() : "Unable to open " + file.getName(),
                            "Cannot Open Video", JOptionPane.ERROR_MESSAGE);
                    failed.run();
                });
                return;
            }
            SwingUtilities.invokeLater(() -> {
                beforeAdd.accept(streamViewer);
                addStream(streamViewer);
                afterAdd.accept(streamViewer);
            });
        }, "open-video-" + file.getName());
        opener.setDaemon(true);
        opener.start();
    }

    /**
     * Tells the Data Controller if shift is being held or not.
     *
     * @param shift True for shift held; false otherwise.
     */
    public void setShiftMask(boolean shift) {
        shiftMask = shift;
    }

    /**
     * @param clockTime Current clockTimer time in milliseconds.
     */
    @Override
    public void clockStart(double clockTime) {
        logger.info("Start");
    }

    /**
     * Keep track of whether this should be visible or not
     */
    public boolean shouldBeVisible() {
        return visible;
    }

    public void setShouldBeVisible(boolean v) {
        visible = v;
    }

    @Override

    public void clockForceSync(double clockTime) {
        logger.debug("Forced sync to " + clockTime + " milliseconds.");
        // Updates the position of the needle and label
        updateCurrentTimeLabelAndNeedle((long) clockTime);
    }

    @Override
    public void clockBoundaryCheck(double clockTime) {
        long stop = mediaPlayers == null ? -1 : mediaPlayers.stopTime(Settings.get().isStopAtEndOfMedia());
        if (stop >= 0 && clockTime >= stop && !clockTimer.isPaused()) {
            logger.info("Pausing at the end of the media, " + stop);
            clockTimer.pause();
            clockTimer.setForceTime(stop);
            labelSpeed.setText("[" + FloatingPointUtils.doubleToFractionStr(clockTimer.getRate()) + "]");
        }
        if((clockTime >= getTimelineEnd() || clockTime <= 0)
                && !clockTimer.isPaused()){
            logger.info("Clock Boundary Pausing Master Clock at " + clockTime );
            clockTimer.pause();
            labelSpeed.setText("[" + FloatingPointUtils.doubleToFractionStr(clockTimer.getRate())  + "]");
        }

        // Updates the position of the needle and label
        // Check for visible to remove Java Null pointer exception for non-initialised Needle Model
        if (visible) {
            updateCurrentTimeLabelAndNeedle((long) clockTime);
        }
    }

    @Override
    public void streamsBoundaryCheck(double clockTime) {
        // Nothing to do here
    }

    @Override
    public void clockSeekPlayback(double clockTime) {
        // Nothing to do here
    }

    /**
     * This method is used only for the JavaFx player, and Native OSX
     * @param clockTime Current clockTimer time in milliseconds.
     */
    @Override
    public void clockPeriodicSync(double clockTime) {
        // Updates the position of the needle and label
        updateCurrentTimeLabelAndNeedle((long) clockTime);
    }

    /**
     * @param clockTime Current clockTimer time in milliseconds.
     */
    @Override
    public void clockStop(double clockTime) {
        logger.debug("Stop Clock at " + clockTime + " milliseconds.");
        // Updates the position of the needle and label
        updateCurrentTimeLabelAndNeedle((long) clockTime);
    }

    /**
     * @param clockTime Current clockTimer time in milliseconds.
     */
    @Override
    public void clockPause(double clockTime) {
        logger.debug("Pause Clock at " + clockTime + " milliseconds.");
        // Updates the position of the needle and label
        updateCurrentTimeLabelAndNeedle((long) clockTime);
    }

    /**
     * @param rate Current (updated) clockTimer rate.
     */
    @Override
    public void clockRate(float rate) {
        logger.debug("Update rate speed to " + rate);
        labelSpeed.setText(FloatingPointUtils.doubleToFractionStr(rate));
    }

    /**
     * @return The end of the timeline, the latest end of any track, in milliseconds.
     */
    private long getTimelineEnd() {
        return mixerController.getMixerModel().getViewportModel().getViewport().getMaxEnd();
    }

    private void updateCurrentTimeLabelAndNeedle(long currentTime) {
        long currentTimeInRange = clockTimer.toRange(currentTime);

        timeStampLabel.setText(formatTimeHtml(currentTimeInRange));
        mixerController.getMixerModel().getNeedleModel().setCurrentTime(currentTimeInRange);
    }

    /**
     * Get the current master clockTimer time for the controller.
     *
     * @return Time in milliseconds.
     */
    public long getCurrentTime() {
        return (long) clockTimer.getClockTime();
    }

    /**
     * Set time location for data streams.
     *
     * @param milliseconds The millisecond time.
     */
    public void setCurrentTime(final long milliseconds) {
        clockTimer.setForceTime(milliseconds);
    }

    /**
     * Fits the timeline to the media: it ends where the last clip ends.
     */
    public void updateMaxViewerDuration() {
        long maxDuration = ViewportStateImpl.MINIMUM_MAX_END;

        for (StreamViewer streamViewer : streamViewers) {
            if ((streamViewer.getDuration() + streamViewer.getOffset()) > maxDuration) {
                maxDuration = streamViewer.getDuration() + streamViewer.getOffset();
            }
        }

        mixerController.setTimelineEnd(maxDuration);

        if (streamViewers.isEmpty()) {
            mixerController.getNeedleController().resetNeedlePosition();
        }
    }

    /**
     * Get the stream viewer with the identifier.
     *
     * @param id The identifier for the stream viewer.
     *
     * @return A stream viewer if found otherwise null.
     */
    private StreamViewer getStreamViewer(final Identifier id) {
        for (StreamViewer streamViewer : streamViewers) {
            if (streamViewer.getIdentifier().equals(id)) {
                return streamViewer;
            }
        }
        return null;
    }

    /**
     * Remove the specified viewer from the controller.
     *
     * @param id The identifier of the viewer to shutdown.
     */
    public void shutdown(final Identifier id, boolean confirm) {
        StreamViewer streamViewer = getStreamViewer(id);

        if ((streamViewer == null) || (confirm && !shouldRemove())) {
            return;
        }

        streamViewers.remove(streamViewer);

        streamViewer.close();

        // Remove the frame rate, this is problematic if we have several tracks with the same frame rate
        frameRateController.removeFrameRate(streamViewer.getIdentifier().asLong());

        // Recalculate the maximum playback duration
        updateMaxViewerDuration();

        updateStepSizeTextField();

    }

    public void shutdown(final Identifier id){
        shutdown(id, true);
    }

    /**
     * Presents a confirmation dialog when removing a plugin from the project.
     *
     * @return True if the plugin should be removed, false otherwise.
     */
    private boolean shouldRemove() {
        Resources rMap = Resources.of(Datavyu.class);
        return Dialogs.confirm(this, rMap.getString("ClosePluginDialog.message"),
                rMap.getString("ClosePluginDialog.title"), "OK");
    }

    /**
     * Helper method for Building a button for the data controller - sets the icon, selected icon, action map, and name.
     *
     * @param name Prefix when looking for actions and buttons.
     * @param modifier Modifier (if any) to apply to the prefix. Maybe null.
     * @param action What the button does.
     * @return A configured button.
     */
    private JButton buildButton(final String name, final String modifier, final Runnable action) {
        String dotModifier = (modifier == null) || modifier.isEmpty() ? "" : "." + modifier;
        JButton jButton = new JButton();
        jButton.addActionListener(e -> action.run());
        jButton.setIcon(resourceMap.getIcon(name + "Button.icon" + dotModifier));
        jButton.setPressedIcon(resourceMap.getIcon(name + "SelectedButton.icon" + dotModifier));
        jButton.setFocusPainted(false);
        jButton.setName(name + "Button");
        return jButton;
    }


    private JPanel makeLabelAndTextFieldPanel(JLabel label, JTextField textField) {
        JPanel jPanel = new JPanel();
        label.setFont(TEXT_LABEL_FONT);
        textField.setFont(TEXT_FIELD_FONT);
        jPanel.add(label);
        jPanel.add(textField);
        return jPanel;
    }

    /**
     * Helper method for creating placeholder buttons
     */
    private JButton makePlaceholderButton(boolean visible) {
        JButton jButton = new JButton();
        jButton.setEnabled(false);
        jButton.setFocusPainted(false);
        jButton.setVisible(visible);
        return jButton;
    }

    /**
     * Helper method for creating placeholder buttons
     */
    private JButton makePlaceholderButton() {
        return makePlaceholderButton(true);
    }

    /**
     * Initialize the view for OS other than Macs.
     */
    private void initComponents() {
        gridButtonPanel = new JPanel();
        jumpByTextField = new JTextField();
        stepSizeTextField = new JTextField();
        onsetTextField = new JTextField();
        JButton addDataButton = new JButton();
        timeStampLabel = new JLabel();
        labelSpeed = new JLabel();
        createNewCell = new JButton();
        JLabel atLabel = new JLabel();
        JLabel xLabel = new JLabel();
        offsetTextField = new JTextField();
        tracksPanel = new JPanel(new MigLayout("fill"));

        final int fontSize = 11;

        setTitle(resourceMap.getString("title"));
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(
                    final java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        gridButtonPanel.setBackground(Color.WHITE);
        gridButtonPanel.setLayout(new MigLayout("wrap 5"));

        // Add data button
        addDataButton.setText(resourceMap.getString("addDataButton.text"));
        addDataButton.setFont(new Font("Tahoma", Font.PLAIN, fontSize));
        addDataButton.setFocusPainted(false);
        addDataButton.setName("addDataButton");
        addDataButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent evt) {
                openVideoButtonActionPerformed(evt);
            }
        });
        gridButtonPanel.add(addDataButton, "span 2, w 90!, h 25!, wrap");

        // Timestamp panel
        JPanel timestampPanel = new JPanel(new MigLayout("","push[][][]0![]push"));
        timestampPanel.setOpaque(false);

        // Timestamp label
        timeStampLabel.setFont(new Font("Tahoma", Font.BOLD, timeStampFontSize));
        timeStampLabel.setHorizontalAlignment(SwingConstants.CENTER);
        timeStampLabel.setText("00:00:00:000");
        timeStampLabel.setHorizontalTextPosition(SwingConstants.CENTER);
        timeStampLabel.setName("timeStampLabel");
        timeStampLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Open a new frame here as i have assumed you have declared "frame" as instance variable
                if (e.getClickCount() >= 3) {
                    int newTime = Integer.parseInt(JOptionPane.showInputDialog(null,
                            "Enter new time in ms", getCurrentTime()));
                    setCurrentTime(newTime);
                }
            }
        });
        timeStampLabel.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int count = e.getWheelRotation();
                timeStampFontSize = Math.min(72, Math.max(10, timeStampFontSize + count));
                timeStampLabel.setFont(new Font("Tahoma", Font.BOLD, timeStampFontSize));
                gridButtonPanel.setMinimumSize(timestampPanel.getMinimumSize());
                timeStampLabel.repaint();
                pack();
                validate();
            }
        });
        timestampPanel.add(timeStampLabel);

        atLabel.setText("@");
        timestampPanel.add(atLabel);

        labelSpeed.setFont(new Font("Tahoma", Font.BOLD, timeStampFontSize));
        labelSpeed.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1,
                2));
        labelSpeed.setName("labelSpeed");
        labelSpeed.setText("0");
        timestampPanel.add(labelSpeed);

        xLabel.setFont(new Font("Tahoma", Font.BOLD, fontSize));
        xLabel.setText("x");
        timestampPanel.add(xLabel);

        gridButtonPanel.add(timestampPanel, "north");

        // Placeholder at top, left: 'clear' or 'numlock' position
        gridButtonPanel.add(makePlaceholderButton(), NUM_PAD_KEY_SIZE);

        // Point cell: equals sign on the Mac keypad
        pointCellButton = buildButton("pointCell", null, this::pointCellAction);
        gridButtonPanel.add(pointCellButton, NUM_PAD_KEY_SIZE);

        // Show/hide: forward slash on the Mac keypad
        showTracksSmallButton = new JButton();
        showTracksSmallButton.setIcon(resourceMap.getIcon(
                "showTracksSmallButton.hide.icon"));
        showTracksSmallButton.setName("showTracksSmallButton");
        showTracksSmallButton.getAccessibleContext().setAccessibleName(
                "Show Tracks");
        showTracksSmallButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent evt) {
                showTracksButtonActionPerformed(evt);
            }
        });
        showTracksSmallButton.setFocusPainted(false);
        gridButtonPanel.add(showTracksSmallButton, NUM_PAD_KEY_SIZE);

        // Placeholder at asterisk location
        gridButtonPanel.add(makePlaceholderButton(), NUM_PAD_KEY_SIZE);
        // Placeholder - perhaps eventually the sync video button
        gridButtonPanel.add(makePlaceholderButton(false), WIDE_TEXT_FIELD_SIZE);

        // Set cell onset button with 7
        setCellOnsetButton = buildButton("setCellOnset", null, this::setCellOnsetAction);
        gridButtonPanel.add(setCellOnsetButton, NUM_PAD_KEY_SIZE);

        // Play video button with 8
        playButton = buildButton("start", null, this::startAction);
        playButton.setRequestFocusEnabled(false);
        gridButtonPanel.add(playButton, NUM_PAD_KEY_SIZE);

        // Set cell offset button with 9
        nineSetCellOffsetButton = buildButton("setCellOffset", "nine", this::setCellOffsetAction);
        gridButtonPanel.add(nineSetCellOffsetButton, NUM_PAD_KEY_SIZE);

        addJumpByPair();

        // Shuttle back button with 4
        shuttleBackButton = buildButton("shuttleBack", null, this::shuttleBackAction);
        gridButtonPanel.add(shuttleBackButton, NUM_PAD_KEY_SIZE);

        // Stop button with 5
        stopButton = buildButton("stop", null, this::stopAction);
        gridButtonPanel.add(stopButton, NUM_PAD_KEY_SIZE);

        // Shuttle forward button with 6
        shuttleForwardButton = buildButton("shuttleForward", null, this::shuttleForwardAction);
        gridButtonPanel.add(shuttleForwardButton, NUM_PAD_KEY_SIZE);


        // Find button (small plus)
        findButton = buildButton("find", "osx", this::findAction);
        gridButtonPanel.add(findButton, NUM_PAD_KEY_SIZE);
        addStepSizePanel();

        // Jog back button with 1
        JButton jogBackButton = buildButton("jogBack", null, this::jogBackAction);
        gridButtonPanel.add(jogBackButton, NUM_PAD_KEY_SIZE);

        // Pause button with 2
        pauseButton = buildButton("pause", null, this::pauseAction);
        gridButtonPanel.add(pauseButton, NUM_PAD_KEY_SIZE);

        // Jog forward button with 3
        JButton jogForwardButton = buildButton("jogForward", null, this::jogForwardAction);
        gridButtonPanel.add(jogForwardButton, NUM_PAD_KEY_SIZE);

        // Create new cell button with enter
        createNewCell = buildButton("createNewCell", null, this::createNewCellAction);
        createNewCell.setAlignmentY(0.0F);
        createNewCell.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        gridButtonPanel.add(createNewCell, TALL_NUM_PAD_KEY_SIZE);

        // Onset text field
        onsetTextField.setHorizontalAlignment(SwingConstants.CENTER);
        onsetTextField.setText("00:00:00:000");
        onsetTextField.setToolTipText(resourceMap.getString(
                "onsetTextField.toolTipText"));
        onsetTextField.setName("findOnsetLabel");
        gridButtonPanel.add(makeLabelAndTextFieldPanel(new JLabel("Onset"), onsetTextField), WIDE_TEXT_FIELD_SIZE);

        // Create new cell setting offset button with zero
        createNewCellSettingOffset = buildButton("createNewCellAndSetOffset", null, this::createNewCellAndSetOffsetAction);
        gridButtonPanel.add(createNewCellSettingOffset, "span 2, w 95!, h 45!");

        // Set cell offset button
        setCellOffsetButton = buildButton("setCellOffset", "period", this::setCellOffsetAction);
        gridButtonPanel.add(setCellOffsetButton, NUM_PAD_KEY_SIZE);

        // Offset text field
        offsetTextField.setHorizontalAlignment(SwingConstants.CENTER);
        offsetTextField.setText("00:00:00:000");
        offsetTextField.setToolTipText(resourceMap.getString("offsetTextField.toolTipText"));
        offsetTextField.setEnabled(false); // Do we really want this? i don't see what makes it different from onset
        offsetTextField.setName("findOffsetLabel");
        gridButtonPanel.add(makeLabelAndTextFieldPanel(new JLabel("Offset"), offsetTextField),
                WIDE_TEXT_FIELD_SIZE);

        getContentPane().setLayout(new MigLayout("ins 0, hidemode 3, fillx", "[growprio 0]0[]", ""));
        getContentPane().add(gridButtonPanel, "");
        getContentPane().setBackground(Color.WHITE);

        tracksPanel.setBackground(Color.WHITE);
        tracksPanel.setVisible(false);
        getContentPane().add(tracksPanel, "growx");

        pack();
    }

    private void addJumpByPair() {
        //Go back button - minus key
        jumpByButton = buildButton("jumpBy", null, this::jumpByAction);
        gridButtonPanel.add(jumpByButton, NUM_PAD_KEY_SIZE);
        //Go back text field
        jumpByTextField.setHorizontalAlignment(SwingConstants.CENTER);
        jumpByTextField.setText("00:00:05:000");
        jumpByTextField.setName("jumpByTextField");
        gridButtonPanel.add(makeLabelAndTextFieldPanel(new JLabel("Jump by"), jumpByTextField),
                WIDE_TEXT_FIELD_SIZE);
    }

    private void addStepSizePanel() {
        // Go back text field
        stepSizeTextField.setHorizontalAlignment(SwingConstants.CENTER);
        stepSizeTextField.setName("stepSizeTextField");
        stepSizeTextField.setToolTipText("Double click to change");
        stepSizeTextField.setPreferredSize(new Dimension(WIDE_TEXT_FIELD_WIDTH - 10, 18));
        stepSizeTextField.setEnabled(false);
        stepSizePanel = makeLabelAndTextFieldPanel(stepSizeLabel, stepSizeTextField);
        gridButtonPanel.add(stepSizePanel, WIDE_TEXT_FIELD_SIZE);
        updateStepSizeTextField();
    }

    private void updateStepSizeTextField() {

        // If we don't have any stream viewers, disable the label and remove the user-defined rate
         if (frameRateController.isZeroRate()) {
            stepSizeTextField.setText("");
        } else {
            stepSizeTextField.setText(Float.toString(frameRateController.getFrameRate()));
        }
    }

    private void updateStepSizePanelColor() {
        boolean assumedFps = false;
        if (streamViewers != null) {
            for (StreamViewer streamViewer : streamViewers) {
                if (streamViewer.isAssumedFramesPerSecond()) {
                    assumedFps = true;
                }
            }
        }
        stepSizePanel.setBackground(assumedFps ? Color.RED : Color.LIGHT_GRAY);
    }

    /**
     * Action to invoke when the user closes the data controller.
     *
     * @param evt The event that triggered this action.
     */
    private void formWindowClosing(final WindowEvent evt) {
        setVisible(false);
        visible = false;
    }

    /**
     * Action to invoke when the user clicks on the open button.
     *
     * @param evt The event that triggered this action.
     */
    private void openVideoButtonActionPerformed(final ActionEvent evt) {
        logger.info("Add data");

        DatavyuFileChooser chooser = new DatavyuFileChooser();
        chooser.addChoosableFileFilter(JavaFxVideoPlugin.VIDEO_FILE_FILTER);
        chooser.setFileFilter(JavaFxVideoPlugin.VIDEO_FILE_FILTER);

        if (JFileChooser.APPROVE_OPTION == chooser.showOpenDialog(this)) {
            openVideo(chooser.getSelectedFile());
        }
    }

    /**
     * Action to invoke when the user clicks the show tracks button.
     *
     * @param evt The event that triggered this action.
     */
    private void showTracksButtonActionPerformed(ActionEvent evt) {

        // TODO: Fix this assert through proper error handling
        assert (evt.getSource() instanceof JButton);

        JButton button = (JButton) evt.getSource();
        Resources resourceMap = Resources.of(VideoController.class);

        if (tracksPanelVisible) {
            logger.info("Show tracks (" + button.getName() + ")");
            // Panel is being displayed, hide it
            button.setIcon(resourceMap.getIcon(button.getName() + ".show.icon"));
            button.setPressedIcon(resourceMap.getIcon(button.getName() + "Selected.show.icon"));
        } else {
            logger.info("Hide tracks (" + button.getName() + ")");
            // Panel is hidden, show it
            button.setIcon(resourceMap.getIcon(button.getName() + ".hide.icon"));
            button.setPressedIcon(resourceMap.getIcon(button.getName() + "Selected.hide.icon"));
        }

        tracksPanelVisible = !tracksPanelVisible;
        showTracksPanel(tracksPanelVisible);
    }

    /**
     * Returns set of StreamViewers.
     *
     * @return a set of StreamViewers.
     */
    public LinkedHashSet<StreamViewer> getStreamViewers() {
        return streamViewers;
    }

    /**
     * Adds a data streamViewer to this data controller.
     *
     * @param streamViewer    The new streamViewer that we are adding to the data controller
     */
    public void addStream(final StreamViewer streamViewer) {
        assert streamViewer.getIdentifier() != null;

        // Add the streamViewer
        streamViewers.add(streamViewer);

        // Adjust the overall frame rate
        frameRateController.addFrameRate(streamViewer.getIdentifier().asLong(), streamViewer.getFramesPerSecond());

        updateStepSizeTextField();
        updateStepSizePanelColor();

        updateMaxViewerDuration();
    }

    /**
     * Action to invoke when the user clicks the set cell onset button.
     */
    public void setCellOnsetAction() {
        logger.info("Set cell onset");
        new SetSelectedCellStartTimeController(getCurrentTime());
        setOnsetField(getCurrentTime());
    }

    /**
     * Action to invoke when the user clicks on the set cell offset button
     */
    public void setCellOffsetAction() {
        logger.info("Set cell offset");
        new SetSelectedCellStopTimeController(getCurrentTime());
        setOffsetField(getCurrentTime());
    }

    /**
     * @param show true to show the tracks layout, false otherwise
     */
    private void showTracksPanel(final boolean show) {
        updateCurrentTimeLabelAndNeedle(getCurrentTime());
        tracksPanel.setVisible(show);
        tracksPanel.repaint();
        pack();
        validate();
    }

    /**
     * Handler for a TracksControllerEvent.
     *
     * @param e event
     */
    @Override
    public void tracksControllerChanged(final TracksControllerEvent e) {
        switch (e.getEventType()) {
            case TIMESCALE_EVENT:
                handleTimescaleEvent((TimescaleEvent) e.getEventObject());
                break;
            default:
                break;
        }
    }

    /**
     * Handles a TimescaleEvent that is created when pulling on the needle with the mouse
     *
     * @param e The timescale event that triggered this action
     */
    private void handleTimescaleEvent(final TimescaleEvent e) {
        logger.info("Change time to " + e.getTime() + " milliseconds and toggle: " + e.getToggleStartStop());

        // Set the time
        clockTimer.setForceTime(e.getTime());

        // Toggle
        if (e.getToggleStartStop()) {
            clockTimer.toggle();
        }
    }

    private void handleNeedleChange(final NeedleState needle) {
        // Nothing to do here, since we update the needle based on the clock timer and not vice versa
        // Notice, that dragging on the needle is handled through "handleTimescaleEvent"
    }

    private void handleViewportChange(final ViewportState viewport) {
        clockTimer.setMaxTime(viewport.getMaxEnd());
    }

    /**
     * Simulates start button clicked.
     */
    public void pressPlay() {
        playButton.doClick();
    }

    public void pressShowTracksSmall() {
        showTracksSmallButton.doClick();
    }

    /**
     * Simulates pause button clicked.
     */
    public void pressPause() {
        pauseButton.doClick();
    }

    /**
     * Simulates stop button clicked.
     */
    public void pressStop() {
        stopButton.doClick();
    }

    /**
     * Simulates shuttle forward button clicked.
     */
    public void pressShuttleForward() {
        shuttleForwardButton.doClick();
    }

    /**
     * Simulates shuttle back button clicked.
     */
    public void pressShuttleBack() {
        shuttleBackButton.doClick();
    }

    /**
     * Simulates find button clicked.
     */
    public void pressFind() {
        findButton.doClick();
    }

    /**
     * Simulates set cell onset button clicked.
     */
    public void pressSetCellOnset() {
        setCellOnsetButton.doClick();
    }

    /**
     * Simulates set cell offset button clicked.
     */
    public void pressSetCellOffsetPeriod() {
        setCellOffsetButton.doClick();
    }

    /**
     * Simulates set cell offset button clicked.
     */
    public void pressSetCellOffsetNine() {
        nineSetCellOffsetButton.doClick();
    }

    /**
     * Simulates set new cell onset button clicked.
     */
    public void pressPointCell() {
        pointCellButton.doClick();
    }

    /**
     * Simulates go back button clicked.
     */
    public void pressJumpBack() {
        jumpDirection = -1;
        jumpByButton.doClick();
    }

    /**
     * Simulates go back button clicked.
     */
    public void pressJumpForward() {
        jumpDirection = 1;
        jumpByButton.doClick();
    }

    /**
     * Simulates create new cell button clicked.
     */
    public void pressCreateNewCell() {
        createNewCell.doClick();
    }

    /**
     * Simulates create new cell setting offset button clicked.
     */
    public void pressCreateNewCellSettingOffset() {
        createNewCellSettingOffset.doClick();
    }

    /**
     * Action to invoke when the user clicks on the start button.
     */
    public void startAction() {
        logger.info("Play");
        clockTimer.setRate(1f);
    }

    /**
     * Action to invoke when the user clicks on the pause button.
     */
    public void pauseAction() {
        // Toggle between isPlaying and not isPlaying
        if (clockTimer.isPaused() && !clockTimer.isStopped()) {
            logger.info("Resume playback at rate: " + clockTimer.getRate());
            clockTimer.start();
            labelSpeed.setText(FloatingPointUtils.doubleToFractionStr(clockTimer.getRate()));
        } else {
            logger.info("Pause playback at rate: " + clockTimer.getRate());
            clockTimer.pause();
            clockTimer.setForceTime((long) clockTimer.getStreamTime());
            labelSpeed.setText("[" + FloatingPointUtils.doubleToFractionStr(clockTimer.getRate())  + "]");
        }
    }

    /**
     * Action to invoke when the user clicks on the stop button.
     */
    public void stopAction() {
        logger.info("Stop.");
        clockTimer.setRate(0f);
        clockTimer.setForceTime((long) clockTimer.getClockTime());
    }

    /**
     * Action to invoke when the user clicks on the shuttle forward button.
     */
    public void shuttleForwardAction() {
        logger.info("Shuttle forward.");
        shuttle(1);
    }

    /**
     * Action to invoke when the user clicks on the shuttle back button.
     */
    public void shuttleBackAction() {
        logger.info("Shuttle back.");
        shuttle(-1);
    }

    /**
     * Populates the find time in the controller.
     *
     * @param milliseconds The time to use when populating the find field.
     */
    public void setOnsetField(final long milliseconds) {
        onsetTextField.setText(Timestamp.format(milliseconds));
    }

    /**
     * Populates the find offset time in the controller.
     *
     * @param milliseconds The time to use when populating the find field.
     */
    public void setOffsetField(final long milliseconds) {
        offsetTextField.setText(Timestamp.format(milliseconds));
    }

    /**
     * Action to invoke when the user clicks on the find button.
     */
    public void findAction() {
        if (shiftMask) {
            findOffsetAction();
        } else {

            try {
                long onset = Timestamp.parse(onsetTextField.getText());
                logger.info("Finding to " + onsetTextField.getText() + " " + onset);
                clockTimer.setForceTime(onset);
            } catch (IllegalArgumentException e) {
                logger.error("unable to find within video", e);
            }
        }
    }

    /**
     * Action to invoke when the user holds shift down.
     */
    public void findOffsetAction() {
        try {
            clockTimer.setForceTime(Timestamp.parse(offsetTextField.getText()));
        } catch (IllegalArgumentException e) {
            logger.error("Unable to find offset " + e);
        }
    }

    public boolean getCellHighlighting() {
        return highlightCells;
    }

    /** Turns highlight-and-focus mode on or off; cells are highlighted exactly while it is on. */
    public void toggleCellHighlightingAutoFocus() {
        highlightAndFocus = !highlightAndFocus;
        highlightCells = highlightAndFocus;
    }

    public boolean getCellHighlightAndFocus() {
        return highlightAndFocus;
    }

    /**
     * Action to invoke when the user clicks on the go back button.
     */
    public void jumpByAction() {
        try {
            long backTime = jumpDirection * Timestamp.parse(jumpByTextField.getText());
            long newTime = (long) clockTimer.getClockTime() + backTime;
            logger.info("Jump back by " +  backTime + " From: " + clockTimer.getStreamTime() + " To: " + newTime);
            clockTimer.setForceTime(newTime);

        } catch (IllegalArgumentException e) {
            logger.error("Unable to jump back " + e);
        }
    }

    /**
     * Action to invoke when the user clicks on the jog backwards button.
     */
    public void jogBackAction() {
        logger.info("Jog back");
        if (!clockTimer.isStopped()) {
            clockTimer.setRate(0f);
        } else {
            syncStreams();
            double frameRate = frameRateController.getFrameRate();
            long clockTime = (long) clockTimer.getClockTime();
            long stepSize = (long) Math.ceil(MILLI_IN_SEC / frameRate); // step size is in milliseconds
            // Update the clock timer with the new time
            long newTime = clockTime - (clockTime % stepSize) - stepSize;
            // A viewer that steps by frame lands on an exact frame time; the clock follows it
            Long steppedTime = null;
            for (StreamViewer streamViewer : streamViewers) {
                if (streamViewer.isStepEnabled()
                    && streamViewer.getFramesPerSecond() == frameRate) {
                    streamViewer.stepBackward();
                    if (steppedTime == null) {
                        steppedTime = streamViewer.getCurrentTime() + streamViewer.getOffset();
                    }
                } else {
                    // Get the stream time
                    long trackTime = newTime - streamViewer.getOffset();

                    // Notice that the new time is in jogs to frame markers by being modulo step size
                    long newStreamTime = Math.min(Math.max(trackTime, 0), streamViewer.getDuration());

                    logger.info("Stream " + streamViewer.getIdentifier()
                        + " - Jog back from " + trackTime + " milliseconds to " + newStreamTime + " milliseconds");

                    streamViewer.setCurrentTime(newStreamTime);
                }
                // otherwise we can't step
            }
            if (steppedTime != null) {
                newTime = steppedTime;
            }

            //Force the time in order to update the cell highlighting
            clockTimer.setTime(newTime);

            updateCurrentTimeLabelAndNeedle(newTime);
        }
    }

    /**
     * Get the frame rate controller for this video controller
     *
     * @return The frame rate controller
     */
    public FrameRateController getFrameRateController() {
        return frameRateController;
    }

    /**
     * Force sync between streams up to a threshold
     */
    private void syncStreams() {
        long clockTime = (long) clockTimer.getClockTime();
        for (StreamViewer streamViewer : streamViewers) {
            // Get the stream time
            long trackTime = clockTime - streamViewer.getOffset();
            if (Math.abs(trackTime - streamViewer.getCurrentTime()) >= ClockTimer.SYNC_THRESHOLD) {
                streamViewer.setCurrentTime(trackTime);
            }
        }
    }

    /**
     * Action to invoke when the user clicks on the jog forwards button.
     */
    public void jogForwardAction() {
        logger.info("Jog forward");
        if (!clockTimer.isStopped()) {
            clockTimer.setRate(0);
        } else {
            syncStreams();
            double frameRate = frameRateController.getFrameRate();
            long clockTime = (long) clockTimer.getClockTime();
            long stepSize = (long) Math.ceil(MILLI_IN_SEC / frameRate); // step size is in milliseconds

            // Update the clock timer with the new time
            long newTime = clockTime - (clockTime % stepSize) + stepSize;
            // A viewer that steps by frame lands on an exact frame time; the clock follows it
            Long steppedTime = null;
            for (StreamViewer streamViewer : streamViewers) {
                if (streamViewer.isStepEnabled()
                        && streamViewer.getFramesPerSecond() == frameRate) {
                    streamViewer.stepForward();
                    if (steppedTime == null) {
                        steppedTime = streamViewer.getCurrentTime() + streamViewer.getOffset();
                    }
                } else {
                    // Get the stream time
                    long trackTime = newTime - streamViewer.getOffset();

                    // Notice that the new time is in jogs to frame markers by being modulo step size
                    long newStreamTime = Math.min(Math.min(Math.max(trackTime, 0), streamViewer.getDuration()),
                            getTimelineEnd());

                    logger.info("Stream " + streamViewer.getIdentifier()
                        + " - Jog forward from " + trackTime + " milliseconds to " + newStreamTime + " milliseconds.");

                    streamViewer.setCurrentTime(newStreamTime);
                }
                // otherwise we can't step
            }
            if (steppedTime != null) {
                newTime = steppedTime;
            }
            //Force the time in order to update the cell highlighting
            clockTimer.setTime(newTime);
            updateCurrentTimeLabelAndNeedle(newTime);
        }
    }

    /**
     * @param shuttleJump The required rate and direction of the shuttle.
     */
    private void shuttle(int shuttleJump) {
        if (clockTimer.getClockTime() > 0 && clockTimer.getClockTime() < getTimelineEnd()) {
            float currentRate = clockTimer.getRate();
            boolean reverseAllowed = streamViewers.stream().allMatch(StreamViewer::isReversePlaybackSupported);
            float nextRate = shuttleRates.nextRate(currentRate, shuttleJump, reverseAllowed);
            clockTimer.setRate(nextRate);
            logger.info("Changed rate from " + currentRate + " to " + nextRate + " for " + shuttleJump + " jumps.");
        }
    }

    /**
     * Action to invoke when the user clicks on the create new cell button.
     */
    public void createNewCellAction() {
        logger.info("New cell");

        // Ensure precise sync up
        long time = getCurrentTime();
        if (!clockTimer.isPaused()) {
            clockTimer.setForceTime(time);
        }

        CreateNewCellController controller = new CreateNewCellController();
        controller.createDefaultCell(true);
    }

    /**
     * Action to invoke when the user clicks on the new cell button.
     */
    public void createNewCellAndSetOffsetAction() {
        logger.info("New cell set offset");

        // Ensure precise sync up
        long time = getCurrentTime();
        if (!clockTimer.isPaused()) {
            clockTimer.setForceTime(time);
        }

        new CreateNewCellController(time, true);
    }

    /**
     * Action to invoke when the user clicks on the new cell offset button.
     */
    public void pointCellAction() {
        logger.info("Set new cell offset");

        // Set precise clock
        long time = getCurrentTime();
        if (!clockTimer.isPaused()) {
            clockTimer.setForceTime(time);
        }

        new CreateNewCellController(time, false);
        new SetNewCellStopTimeController(time);
        setOffsetField(time);
    }

    @Override
    public void propertyChange(final PropertyChangeEvent e) {
        Object source = e.getSource();
        if (source instanceof NeedleModel) {
            handleNeedleChange(((NeedleModel) source).getNeedle());
        } else if (source instanceof ViewportModel) {
            handleViewportChange(((ViewportModel) source).getViewport());
        }
    }

    public ClockTimer getClockTimer() {
        return clockTimer;
    }
}

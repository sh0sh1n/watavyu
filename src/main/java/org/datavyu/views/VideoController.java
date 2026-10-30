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

import org.datavyu.views.discrete.SpreadsheetTheme;
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
import org.datavyu.models.Identifier;
import org.datavyu.plugins.StreamViewer;
import org.datavyu.plugins.javafx.JavaFxVideoPlugin;
import org.datavyu.util.*;
import org.datavyu.util.ClockTimer.ClockListener;
import org.datavyu.util.Resources;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;


/**
 * Plays a spreadsheet's media: its clock, its players and the playback and coding commands. Its buttons are the
 * controls drawer beside the spreadsheet ({@link #getControls()}); the playhead shows the clock's time
 * ({@link #setPlayhead(Playhead)}).
 */
public final class VideoController implements ClockListener {

    /** Shows the clock's time and rate, from any thread. */
    public interface Playhead {

        /**
         * @param time the clock's time, in milliseconds
         * @param rate the clock's rate, as {@link #rateText(float, boolean)} gives it
         * @param playing whether playback is running, so the sheet follows the needle
         */
        void show(long time, String rate, boolean playing);
    }

    /** Where the timeline ends when no media runs longer: one minute. */
    public static final long MINIMUM_TIMELINE_END = 60_000L;

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

    private static final Font TEXT_FIELD_FONT = SpreadsheetTheme.timeFont(10.5F);

    private static final Font TEXT_LABEL_FONT = SpreadsheetTheme.uiFont(10.5F);

    private static RateController shuttleRates = new RateController();

    /** Determines whether or not the 'shift' key is being held */
    private volatile boolean shiftMask = false;

    /** The set of streamViewers associated with this controller */
    private LinkedHashSet<StreamViewer> streamViewers = new LinkedHashSet<>();

    /** Clock timer */
    private final ClockTimer clockTimer = new ClockTimer();

    /** Where the timeline ends: the end of the media, or a minute if that is sooner. */
    private volatile long timelineEnd = MINIMUM_TIMELINE_END;

    /** Shows the clock's time; nothing until the spreadsheet sets one. */
    private volatile Playhead playhead = (time, rate, playing) -> { };

    /** Keeps the players and the spreadsheet's media columns in step; null until a spreadsheet is set. */
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
    private JPanel gridButtonPanel;

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
    private JButton shuttleBackButton;

    /** */
    private JButton shuttleForwardButton;

    /** */
    private JButton stopButton;

    private Resources resourceMap;

    /**
     * Whether video controllers start in highlight and focus mode, which highlights the cell at the current time in
     * the selected column and focuses its first uncoded argument. The mode needs cell highlighting, so that starts on
     * with it.
     */
    public static final boolean HIGHLIGHT_AND_FOCUS_BY_DEFAULT = true;

    private volatile boolean highlightCells = HIGHLIGHT_AND_FOCUS_BY_DEFAULT;

    private volatile boolean highlightAndFocus = HIGHLIGHT_AND_FOCUS_BY_DEFAULT;

    private FrameRateController frameRateController = new FrameRateController();

    private volatile int jumpDirection = -1;

    /**
     * Makes a spreadsheet's player, stopped at time zero, with its controls.
     */
    public VideoController() {
        clockTimer.registerListener(this);
        resourceMap = Resources.of(VideoController.class);
        initComponents();
        clockTimer.setMinTime(0);
        clockTimer.setMaxTime(MINIMUM_TIMELINE_END);
    }

    /**
     * @return the controls drawer: the playback and coding buttons, laid out as a numeric keypad, and their fields
     */
    public JComponent getControls() {
        return gridButtonPanel;
    }

    /**
     * Sets what shows the clock's time, and shows it the time now.
     *
     * @param playhead shows the time; called from the clock's threads as well as the EDT
     */
    public void setPlayhead(final Playhead playhead) {
        this.playhead = playhead;
        showPlayhead(getCurrentTime());
    }

    /**
     * Sets what keeps the players and the spreadsheet's media columns in step.
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
        updateCurrentTimeLabelAndNeedle((long) clockTime);
    }

    @Override
    public void clockForceSync(double clockTime) {
        logger.debug("Forced sync to " + clockTime + " milliseconds.");
        // Updates the position of the needle and label
        updateCurrentTimeLabelAndNeedle((long) clockTime);
    }

    @Override
    public void clockBoundaryCheck(double clockTime) {
        if (!clockTimer.isPaused()) {
            long stop = mediaPlayers == null ? -1 : mediaPlayers.stopTime(Settings.get().isStopAtEndOfMedia());
            float rate = clockTimer.getRate();
            if (stop >= 0 && rate > 0 && clockTime >= stop) {
                logger.info("Pausing at the end of the media, " + stop);
                clockTimer.pause();
                clockTimer.setForceTime(stop);
            } else if (atTimelineEdge(clockTime, rate, getTimelineEnd())) {
                logger.info("Clock Boundary Pausing Master Clock at " + clockTime);
                clockTimer.pause();
            }
        }
        updateCurrentTimeLabelAndNeedle((long) clockTime);
    }

    /**
     * @param clockTime   the clock's time
     * @param rate        the clock's rate
     * @param timelineEnd the end of the timeline
     * @return whether playback has run into the end it is heading for: the timeline's end going forward, or time
     * zero going backward. Playback starting at either edge and heading away from it carries on.
     */
    static boolean atTimelineEdge(final double clockTime, final float rate, final long timelineEnd) {
        return rate > 0 ? clockTime >= timelineEnd : rate < 0 && clockTime <= 0;
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
        updateCurrentTimeLabelAndNeedle(getCurrentTime());
    }

    /**
     * @return The end of the timeline, the latest end of any track, in milliseconds.
     */
    private long getTimelineEnd() {
        return timelineEnd;
    }

    private void updateCurrentTimeLabelAndNeedle(long currentTime) {
        showPlayhead(clockTimer.toRange(currentTime));
    }

    private void showPlayhead(final long time) {
        playhead.show(time, rateText(clockTimer.getRate(), clockTimer.isPaused()),
                !clockTimer.isPaused() && clockTimer.getRate() != 0F);
    }

    /**
     * @param rate   the clock's rate
     * @param paused whether the clock is paused
     * @return the rate as the playhead shows it: {@code 1.25×} while playing, {@code paused 1.25×} while paused at a
     * rate, and {@code stopped} at rate zero
     */
    static String rateText(final float rate, final boolean paused) {
        if (rate == 0F) {
            return "stopped";
        }
        String text = new java.math.BigDecimal(Float.toString(rate)).stripTrailingZeros().toPlainString() + "×";
        return paused ? "paused " + text : text;
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
        long maxDuration = MINIMUM_TIMELINE_END;

        for (StreamViewer streamViewer : streamViewers) {
            if ((streamViewer.getDuration() + streamViewer.getOffset()) > maxDuration) {
                maxDuration = streamViewer.getDuration() + streamViewer.getOffset();
            }
        }

        timelineEnd = maxDuration;
        clockTimer.setMaxTime(maxDuration);
        updateCurrentTimeLabelAndNeedle(getCurrentTime());
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
        return Dialogs.confirm(Datavyu.getApplication().getMainFrame(), rMap.getString("ClosePluginDialog.message"),
                rMap.getString("ClosePluginDialog.title"), "OK");
    }

    /**
     * Makes a key of the keypad.
     *
     * @param name   prefix of the button's name, which tests find it by
     * @param glyph  what the key shows
     * @param label  what it does, in a word or two
     * @param key    the keypad key that does the same
     * @param action what the key does
     * @return the key
     */
    private JButton buildButton(final String name, final Glyph.Shape glyph, final String label, final String key,
                                final Runnable action) {
        JButton jButton = new KeypadKey(glyph, label, key);
        jButton.addActionListener(e -> action.run());
        jButton.setName(name + "Button");
        return jButton;
    }


    private JPanel makeLabelAndTextFieldPanel(JLabel label, JTextField textField) {
        JPanel jPanel = new JPanel();
        jPanel.setOpaque(false);
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
     * Lays out the controls as a numeric keypad, with the fields beside it.
     */
    private void initComponents() {
        gridButtonPanel = new JPanel();
        jumpByTextField = new JTextField();
        onsetTextField = new JTextField();
        createNewCell = new JButton();
        offsetTextField = new JTextField();

        gridButtonPanel.setName("controls");
        gridButtonPanel.setBackground(SpreadsheetTheme.PALETTE.chrome());
        gridButtonPanel.setLayout(new MigLayout("wrap 5"));

        // Placeholder at top, left: 'clear' or 'numlock' position
        gridButtonPanel.add(makePlaceholderButton(), NUM_PAD_KEY_SIZE);

        // Point cell: equals sign on the Mac keypad
        pointCellButton = buildButton("pointCell", Glyph.Shape.POINT_CELL, "point", "=", this::pointCellAction);
        gridButtonPanel.add(pointCellButton, NUM_PAD_KEY_SIZE);

        // Placeholder at forward slash on the Mac keypad, whose key shows or hides these controls
        gridButtonPanel.add(makePlaceholderButton(), NUM_PAD_KEY_SIZE);

        // Placeholder at asterisk location
        gridButtonPanel.add(makePlaceholderButton(), NUM_PAD_KEY_SIZE);
        // Placeholder - perhaps eventually the sync video button
        gridButtonPanel.add(makePlaceholderButton(false), WIDE_TEXT_FIELD_SIZE);

        // Set cell onset button with 7
        setCellOnsetButton = buildButton("setCellOnset", Glyph.Shape.SET_ONSET, "onset", "7", this::setCellOnsetAction);
        gridButtonPanel.add(setCellOnsetButton, NUM_PAD_KEY_SIZE);

        // Play video button with 8
        playButton = buildButton("start", Glyph.Shape.PLAY, "play", "8", this::startAction);
        playButton.setRequestFocusEnabled(false);
        gridButtonPanel.add(playButton, NUM_PAD_KEY_SIZE);

        // Set cell offset button with 9
        nineSetCellOffsetButton = buildButton("setCellOffset", Glyph.Shape.SET_OFFSET, "offset", "9", this::setCellOffsetAction);
        gridButtonPanel.add(nineSetCellOffsetButton, NUM_PAD_KEY_SIZE);

        addJumpByPair();

        // Shuttle back button with 4
        shuttleBackButton = buildButton("shuttleBack", Glyph.Shape.SHUTTLE_BACK, "shuttle", "4", this::shuttleBackAction);
        gridButtonPanel.add(shuttleBackButton, NUM_PAD_KEY_SIZE);

        // Stop button with 5
        stopButton = buildButton("stop", Glyph.Shape.STOP, "stop", "5", this::stopAction);
        gridButtonPanel.add(stopButton, NUM_PAD_KEY_SIZE);

        // Shuttle forward button with 6
        shuttleForwardButton = buildButton("shuttleForward", Glyph.Shape.SHUTTLE_FORWARD, "shuttle", "6", this::shuttleForwardAction);
        gridButtonPanel.add(shuttleForwardButton, NUM_PAD_KEY_SIZE);


        // Find button (small plus)
        findButton = buildButton("find", Glyph.Shape.FIND, "find", "+", this::findAction);
        gridButtonPanel.add(findButton, NUM_PAD_KEY_SIZE);
        // Where the frame rate was: the grid keeps its shape.
        gridButtonPanel.add(new JLabel(), WIDE_TEXT_FIELD_SIZE);

        // Jog back button with 1
        JButton jogBackButton = buildButton("jogBack", Glyph.Shape.JOG_BACK, "jog", "1", this::jogBackAction);
        gridButtonPanel.add(jogBackButton, NUM_PAD_KEY_SIZE);

        // Pause button with 2
        pauseButton = buildButton("pause", Glyph.Shape.PAUSE, "pause", "2", this::pauseAction);
        gridButtonPanel.add(pauseButton, NUM_PAD_KEY_SIZE);

        // Jog forward button with 3
        JButton jogForwardButton = buildButton("jogForward", Glyph.Shape.JOG_FORWARD, "jog", "3", this::jogForwardAction);
        gridButtonPanel.add(jogForwardButton, NUM_PAD_KEY_SIZE);

        // Create new cell button with enter
        createNewCell = buildButton("createNewCell", Glyph.Shape.NEW_CELL, "new cell", "enter", this::createNewCellAction);
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
        createNewCellSettingOffset = buildButton("createNewCellAndSetOffset", Glyph.Shape.NEW_CELL_PREVIOUS_OFFSET,
                "new + prev offset", "0", this::createNewCellAndSetOffsetAction);
        gridButtonPanel.add(createNewCellSettingOffset, "span 2, w 95!, h 45!");

        // Set cell offset button
        setCellOffsetButton = buildButton("setCellOffset", Glyph.Shape.SET_OFFSET, "offset", ".", this::setCellOffsetAction);
        gridButtonPanel.add(setCellOffsetButton, NUM_PAD_KEY_SIZE);

        // Offset text field
        offsetTextField.setHorizontalAlignment(SwingConstants.CENTER);
        offsetTextField.setText("00:00:00:000");
        offsetTextField.setToolTipText(resourceMap.getString("offsetTextField.toolTipText"));
        offsetTextField.setEnabled(false); // Do we really want this? i don't see what makes it different from onset
        offsetTextField.setName("findOffsetLabel");
        gridButtonPanel.add(makeLabelAndTextFieldPanel(new JLabel("Offset"), offsetTextField),
                WIDE_TEXT_FIELD_SIZE);

    }

    private void addJumpByPair() {
        //Go back button - minus key
        jumpByButton = buildButton("jumpBy", Glyph.Shape.JUMP_BACK, "jump", "−", this::jumpByAction);
        gridButtonPanel.add(jumpByButton, NUM_PAD_KEY_SIZE);
        //Go back text field
        jumpByTextField.setHorizontalAlignment(SwingConstants.CENTER);
        jumpByTextField.setText("00:00:05:000");
        jumpByTextField.setName("jumpByTextField");
        gridButtonPanel.add(makeLabelAndTextFieldPanel(new JLabel("Jump by"), jumpByTextField),
                WIDE_TEXT_FIELD_SIZE);
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

        // Jogging steps by the highest frame rate of the media with video; audio has no frames.
        Dimension size = streamViewer.getVideoSize();
        if (size != null && size.width > 0 && size.height > 0) {
            frameRateController.addFrameRate(streamViewer.getIdentifier().asLong(),
                    streamViewer.getFramesPerSecond());
        }

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
     * Simulates start button clicked.
     */
    public void pressPlay() {
        playButton.doClick();
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
        } else {
            logger.info("Pause playback at rate: " + clockTimer.getRate());
            clockTimer.pause();
            clockTimer.setForceTime((long) clockTimer.getStreamTime());
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
            double frameRate = jogFrameRate();
            long clockTime = (long) clockTimer.getClockTime();
            long stepSize = (long) Math.ceil(MILLI_IN_SEC / frameRate); // step size is in milliseconds
            // Update the clock timer with the new time
            long newTime = clockTime - (clockTime % stepSize) - stepSize;
            // A viewer that steps by frame lands on an exact frame time; the clock follows it
            Long steppedTime = null;
            for (StreamViewer streamViewer : streamViewers) {
                if (streamViewer.isStepEnabled()
                    && streamViewer.getFramesPerSecond() == frameRate
                    && holds(streamViewer, clockTime)) {
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
     * @return whether a clip plays at a clock time; only such a clip steps by frame when jogging, so the clock follows
     * the clip shown rather than another in the same column
     */
    static boolean holds(final StreamViewer clip, final long clockTime) {
        return clockTime >= clip.getOffset() && clockTime < clip.getOffset() + clip.getDuration();
    }

    /** Frames per second jogging steps by when no media with video is open. */
    static final double DEFAULT_JOG_FRAME_RATE = 30;

    private double jogFrameRate() {
        return jogFrameRate(frameRateController.getFrameRate());
    }

    /**
     * @param highestVideoFrameRate the highest frame rate of the open media with video, 0 if none has video
     * @return the frame rate jogging steps by
     */
    static double jogFrameRate(final double highestVideoFrameRate) {
        return highestVideoFrameRate > 0 ? highestVideoFrameRate : DEFAULT_JOG_FRAME_RATE;
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
            double frameRate = jogFrameRate();
            long clockTime = (long) clockTimer.getClockTime();
            long stepSize = (long) Math.ceil(MILLI_IN_SEC / frameRate); // step size is in milliseconds

            // Update the clock timer with the new time
            long newTime = clockTime - (clockTime % stepSize) + stepSize;
            // A viewer that steps by frame lands on an exact frame time; the clock follows it
            Long steppedTime = null;
            for (StreamViewer streamViewer : streamViewers) {
                if (streamViewer.isStepEnabled()
                        && streamViewer.getFramesPerSecond() == frameRate
                        && holds(streamViewer, clockTime)) {
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
        float currentRate = clockTimer.getRate();
        boolean reverseAllowed = streamViewers.stream().allMatch(StreamViewer::isReversePlaybackSupported);
        float nextRate = shuttleRates.nextRate(currentRate, shuttleJump, reverseAllowed);
        clockTimer.setRate(nextRate);
        logger.info("Changed rate from " + currentRate + " to " + nextRate + " for " + shuttleJump + " jumps.");
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

    public ClockTimer getClockTimer() {
        return clockTimer;
    }
}

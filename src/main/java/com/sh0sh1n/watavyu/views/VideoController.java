/*
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
package com.sh0sh1n.watavyu.views;

import com.sh0sh1n.watavyu.views.discrete.SpreadsheetTheme;
import com.sh0sh1n.watavyu.controllers.project.MediaPlayers;
import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.util.Settings;
import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.models.Identifier;
import com.sh0sh1n.watavyu.plugins.StreamViewer;
import com.sh0sh1n.watavyu.plugins.javafx.JavaFxVideoPlugin;
import com.sh0sh1n.watavyu.util.*;
import com.sh0sh1n.watavyu.util.ClockTimer.ClockListener;
import com.sh0sh1n.watavyu.util.Resources;

import javax.swing.*;
import java.awt.*;
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
         * Shows the clock's time and rate.
         *
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
    private final Set<StreamViewer> streamViewers = new LinkedHashSet<>();

    /** Clock timer */
    private final ClockTimer clockTimer = new ClockTimer();

    /** Where the timeline ends: the end of the media, or a minute if that is sooner. */
    private volatile long timelineEnd = MINIMUM_TIMELINE_END;

    /** Shows the clock's time; nothing until the spreadsheet sets one. */
    private volatile Playhead playhead = (time, rate, playing) -> { };

    /** Keeps the players and the spreadsheet's media columns in step; null until a spreadsheet is set. */
    private MediaPlayers mediaPlayers;

    /** Set once the spreadsheet's tab has closed. */
    private volatile boolean closed;

    /** */
    private JTextField offsetTextField;

    /** */
    private JTextField onsetTextField;

    /** */
    private JTextField jumpByTextField;




    /** */
    private JPanel gridButtonPanel;

    private Resources resourceMap;

    /** The drawer's key for each command it has, which {@link #run} presses. */
    private final Map<Command, JButton> keys = new EnumMap<>(Command.class);

    /**
     * Whether video controllers start in highlight and focus mode, which highlights the cell at the current time in
     * the selected column and focuses its first uncoded argument. The mode needs cell highlighting, so that starts on
     * with it.
     */
    public static final boolean HIGHLIGHT_AND_FOCUS_BY_DEFAULT = true;

    private volatile boolean highlightCells = HIGHLIGHT_AND_FOCUS_BY_DEFAULT;

    private volatile boolean highlightAndFocus = HIGHLIGHT_AND_FOCUS_BY_DEFAULT;

    private FrameRateController frameRateController = new FrameRateController();

    /** The spreadsheet whose cells the coding commands change */
    private final ProjectController spreadsheet;

    /** The window that owns the players' windows and this controller's dialogs; null for none */
    private final Frame owner;

    /**
     * Makes a spreadsheet's player, stopped at time zero, with its controls.
     *
     * @param spreadsheet the spreadsheet whose cells the coding commands change
     * @param owner       the window that owns the players' windows and the dialogs; null for none
     */
    public VideoController(final ProjectController spreadsheet, final Frame owner) {
        this.spreadsheet = spreadsheet;
        this.owner = owner;
        clockTimer.registerListener(this);
        resourceMap = Resources.of(VideoController.class);
        initComponents();
        clockTimer.setMinTime(0);
        clockTimer.setMaxTime(MINIMUM_TIMELINE_END);
    }

    /**
     * Returns the controls drawer: the playback and coding buttons, laid out as a numeric keypad, and their fields.
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
     * Returns what keeps the players and the spreadsheet's media columns in step.
     *
     * @return the spreadsheet's media players, or null until its view is made
     */
    public MediaPlayers getMediaPlayers() {
        return mediaPlayers;
    }

    /**
     * Closes the spreadsheet's playback, as its tab closes: playback stops, the clock stops, and then the players and
     * their windows close, so no tick reaches a player as it closes. A player still opening is closed once it opens.
     */
    public void close() {
        closed = true;
        stopAction();
        clockTimer.close();
        if (mediaPlayers != null) {
            mediaPlayers.close();
        }
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
                streamViewer = JavaFxVideoPlugin.open(id, file, owner, clockTimer, false);
            } catch (Throwable t) {
                logger.error("Unable to open " + file, t);
                SwingUtilities.invokeLater(() -> streamFailed(t, file, failed));
                return;
            }
            SwingUtilities.invokeLater(() -> streamOpened(streamViewer, beforeAdd, afterAdd));
        }, "open-video-" + file.getName());
        opener.setDaemon(true);
        opener.start();
    }

    /** Adds a stream that has opened, on the EDT; once this controller is closed, closes it instead. */
    void streamOpened(final StreamViewer streamViewer, final Consumer<StreamViewer> beforeAdd,
                      final Consumer<StreamViewer> afterAdd) {
        if (closed) {
            streamViewer.close();
            return;
        }
        beforeAdd.accept(streamViewer);
        addStream(streamViewer);
        afterAdd.accept(streamViewer);
    }

    /** Shows why a stream did not open, on the EDT; once this controller is closed, its tab is gone and says nothing. */
    void streamFailed(final Throwable cause, final File file, final Runnable failed) {
        if (closed) {
            return;
        }
        JOptionPane.showMessageDialog(owner,
                cause.getMessage() != null ? cause.getMessage() : "Unable to open " + file.getName(),
                "Cannot Open Video", JOptionPane.ERROR_MESSAGE);
        failed.run();
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
     * Returns whether playback has run into the end it is heading for: the timeline's end going forward, or time
     * zero going backward. Playback starting at either edge and heading away from it carries on.
     * @param clockTime   the clock's time
     * @param rate        the clock's rate
     * @param timelineEnd the end of the timeline
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
     * Returns the rate as the playhead shows it: {@code 1.25×} while playing, {@code paused 1.25×} while paused at a
     * rate, and {@code stopped} at rate zero.
     * @param rate   the clock's rate
     * @param paused whether the clock is paused
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
        Resources rMap = Resources.of(VideoController.class);
        return Dialogs.confirm(owner, rMap.getString("ClosePluginDialog.message"),
                rMap.getString("ClosePluginDialog.title"), "OK");
    }

    /**
     * Makes a key of the keypad, the drawer's key for a command, which {@link #run} presses.
     *
     * @param command the command, or null for a key that only the drawer has
     * @param name    prefix of the button's name, which tests find it by
     * @param glyph   what the key shows
     * @param label   what it does, in a word or two
     * @param key     the keypad key that does the same
     * @param action  what the key does
     * @return the key
     */
    private JButton buildButton(final Command command, final String name, final Glyph.Shape glyph, final String label,
                                final String key, final Runnable action) {
        JButton jButton = new KeypadKey(glyph, label, key);
        jButton.addActionListener(e -> action.run());
        jButton.setName(name + "Button");
        if (command != null) {
            keys.put(command, jButton);
        }
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
        offsetTextField = new JTextField();

        gridButtonPanel.setName("controls");
        gridButtonPanel.setBackground(SpreadsheetTheme.PALETTE.chrome());
        // The tall and wide keys span two keys and the 5-pixel gap between them. Visual padding would size keys
        // without their focus rings, so they would draw larger than these sizes.
        gridButtonPanel.setLayout(new MigLayout("wrap 5, novisualpadding, gap 5"));

        // Placeholders along the Mac keypad's top row: clear, equals, slash, and the asterisk, whose key shows or
        // hides these controls
        gridButtonPanel.add(makePlaceholderButton(), NUM_PAD_KEY_SIZE);
        gridButtonPanel.add(makePlaceholderButton(), NUM_PAD_KEY_SIZE);
        gridButtonPanel.add(makePlaceholderButton(), NUM_PAD_KEY_SIZE);
        gridButtonPanel.add(makePlaceholderButton(), NUM_PAD_KEY_SIZE);
        // Placeholder - perhaps eventually the sync video button
        gridButtonPanel.add(makePlaceholderButton(false), WIDE_TEXT_FIELD_SIZE);

        // Set cell onset button with 7
        JButton setCellOnsetButton = buildButton(Command.SET_ONSET, "setCellOnset", Glyph.Shape.SET_ONSET, "onset", "7", this::setCellOnsetAction);
        gridButtonPanel.add(setCellOnsetButton, NUM_PAD_KEY_SIZE);

        // Play video button with 8
        JButton playButton = buildButton(Command.PLAY, "start", Glyph.Shape.PLAY, "play", "8", this::startAction);
        playButton.setRequestFocusEnabled(false);
        gridButtonPanel.add(playButton, NUM_PAD_KEY_SIZE);

        // Set cell offset button with 9
        JButton setCellOffsetButton = buildButton(Command.SET_OFFSET, "setCellOffset", Glyph.Shape.SET_OFFSET, "offset", "9", this::setCellOffsetAction);
        gridButtonPanel.add(setCellOffsetButton, NUM_PAD_KEY_SIZE);

        addJumpByPair();

        // Shuttle back button with 4
        JButton shuttleBackButton = buildButton(Command.SHUTTLE_BACK, "shuttleBack", Glyph.Shape.SHUTTLE_BACK, "shuttle", "4", this::shuttleBackAction);
        gridButtonPanel.add(shuttleBackButton, NUM_PAD_KEY_SIZE);

        // Stop button with 5
        JButton stopButton = buildButton(Command.STOP, "stop", Glyph.Shape.STOP, "stop", "5", this::stopAction);
        gridButtonPanel.add(stopButton, NUM_PAD_KEY_SIZE);

        // Shuttle forward button with 6
        JButton shuttleForwardButton = buildButton(Command.SHUTTLE_FORWARD, "shuttleForward", Glyph.Shape.SHUTTLE_FORWARD, "shuttle", "6", this::shuttleForwardAction);
        gridButtonPanel.add(shuttleForwardButton, NUM_PAD_KEY_SIZE);


        // Find button (small plus)
        JButton findButton = buildButton(null, "find", Glyph.Shape.FIND, "find", "+", this::findAction);
        gridButtonPanel.add(findButton, NUM_PAD_KEY_SIZE);
        // Where the frame rate was: the grid keeps its shape.
        gridButtonPanel.add(new JLabel(), WIDE_TEXT_FIELD_SIZE);

        // Jog back button with 1
        JButton jogBackButton = buildButton(null, "jogBack", Glyph.Shape.JOG_BACK, "jog", "1", this::jogBackAction);
        gridButtonPanel.add(jogBackButton, NUM_PAD_KEY_SIZE);

        // Pause button with 2
        JButton pauseButton = buildButton(Command.PAUSE, "pause", Glyph.Shape.PAUSE, "pause", "2", this::pauseAction);
        gridButtonPanel.add(pauseButton, NUM_PAD_KEY_SIZE);

        // Jog forward button with 3
        JButton jogForwardButton = buildButton(null, "jogForward", Glyph.Shape.JOG_FORWARD, "jog", "3", this::jogForwardAction);
        gridButtonPanel.add(jogForwardButton, NUM_PAD_KEY_SIZE);

        // Create new cell button with enter
        JButton createNewCell = buildButton(Command.NEW_CELL_IN_SELECTED_COLUMN, "createNewCell", Glyph.Shape.NEW_CELL, "new cell", "enter", this::createNewCellAction);
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
        JButton createNewCellSettingOffset = buildButton(Command.NEW_CELL_CLOSING_PREVIOUS, "createNewCellAndSetOffset", Glyph.Shape.NEW_CELL_PREVIOUS_OFFSET,
                "new + prev offset", "0", this::createNewCellAndSetOffsetAction);
        gridButtonPanel.add(createNewCellSettingOffset, "span 2, w 95!, h 45!");

        // Placeholder at the decimal point, which set the offset as 9 does
        gridButtonPanel.add(makePlaceholderButton(), NUM_PAD_KEY_SIZE);

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
        JButton jumpByButton = buildButton(Command.JUMP_BACK, "jumpBy", Glyph.Shape.JUMP_BACK, "jump", "−", () -> jumpBy(-1));
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
        long time = getCurrentTime();
        spreadsheet.cells().setOnsets(time);
        setOnsetField(time);
    }

    /**
     * Action to invoke when the user clicks on the set cell offset button
     */
    public void setCellOffsetAction() {
        logger.info("Set cell offset");
        long time = getCurrentTime();
        spreadsheet.cells().setOffsets(time);
        setOffsetField(time);
    }

    /**
     * Runs a playback or coding command, as its keypad key does. A command whose drawer key does just that is run by
     * pressing the key, which shows the press while the drawer is open. Jogging, which repeats faster than a press can
     * show, jumping forward and finding, whose drawer keys depend on the direction or on Shift, are run directly.
     *
     * @param command the command
     * @throws IllegalArgumentException if it is not a command of the drawer
     */
    public void run(final Command command) {
        switch (command) {
            case JOG_BACK -> jogBackAction();
            case JOG_FORWARD -> jogForwardAction();
            case JUMP_FORWARD -> jumpBy(1);
            case FIND_ONSET -> findOnsetAction();
            case FIND_OFFSET -> findOffsetAction();
            default -> {
                JButton key = keys.get(command);
                if (key == null) {
                    throw new IllegalArgumentException(command + " is not a playback command");
                }
                press(key);
            }
        }
    }

    /** How long a key run from the keypad shows as pressed, as long as a click shows it. */
    private static final int PRESS_SHOWN_MS = 68;

    /** Each drawer key's timer that ends its shown press, restarted by each press so a repeat shows in full. */
    private final Map<JButton, javax.swing.Timer> releases = new HashMap<>();

    /**
     * Runs a drawer key's command and shows the key pressed for a moment. {@link JButton#doClick()} would show it
     * by holding the event thread for the whole press, which every keypad key would pay.
     */
    private void press(final JButton key) {
        key.doClick(0);
        ButtonModel model = key.getModel();
        model.setArmed(true);
        model.setPressed(true);
        releases.computeIfAbsent(key, k -> {
            javax.swing.Timer release = new javax.swing.Timer(PRESS_SHOWN_MS, e -> {
                // Disarmed first, so letting go does not run the command again.
                model.setArmed(false);
                model.setPressed(false);
            });
            release.setRepeats(false);
            return release;
        }).restart();
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

    /** The drawer's find key: to the onset field's time, or with Shift held to the offset field's. */
    private void findAction() {
        if (shiftMask) {
            findOffsetAction();
        } else {
            findOnsetAction();
        }
    }

    /** Goes to the onset field's time. */
    private void findOnsetAction() {
        try {
            long onset = Timestamp.parse(onsetTextField.getText());
            logger.info("Finding to " + onsetTextField.getText() + " " + onset);
            clockTimer.setForceTime(onset);
        } catch (IllegalArgumentException e) {
            logger.error("unable to find within video", e);
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
     * Jumps by the jump field's time.
     *
     * @param direction -1 to jump back, 1 to jump forward
     */
    private void jumpBy(final int direction) {
        try {
            long backTime = direction * Timestamp.parse(jumpByTextField.getText());
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
     * Returns whether a clip plays at a clock time; only such a clip steps by frame when jogging, so the clock follows
     * the clip shown rather than another in the same column.
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
     * Returns the frame rate jogging steps by.
     * @param highestVideoFrameRate the highest frame rate of the open media with video, 0 if none has video
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

        spreadsheet.cells().newCellInSelectedColumn(time);
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

        spreadsheet.cells().newCellClosingPrevious(time);
    }

    public ClockTimer getClockTimer() {
        return clockTimer;
    }
}

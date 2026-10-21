package org.datavyu.controllers.project;

import org.datavyu.Datavyu;
import org.datavyu.models.Identifier;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.CellListener;
import org.datavyu.models.db.CellValue;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreListener;
import org.datavyu.models.db.MediaClip;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.datavyu.models.db.VariableListener;
import org.datavyu.plugins.StreamViewer;
import org.datavyu.projectfile.MediaColumnNames;
import org.datavyu.util.ClockTimer;
import org.datavyu.views.MediaWindow;
import org.datavyu.views.VideoController;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plays the spreadsheet's media columns. The spreadsheet holds each column and its clips; this opens a player for each
 * clip whose file exists, and keeps the players and the spreadsheet in step: a clip added, moved or removed in the
 * spreadsheet (by editing, undo or opening a project) opens, moves or closes its player. A clip whose file is missing
 * keeps its place and what is known about it, and has no player.
 *
 * <p>Each media column has one window. At any time it shows the clip in play: of the clips whose span holds the time,
 * the one that starts latest (the later clip wins). Where no clip is in play, or the clip in play has no player, the
 * window is black. A clip not in play is paused and silent.
 *
 * <p>Everything here runs on the Swing EDT, except the clock's calls, which only read a snapshot of each column's clips.
 */
public final class MediaPlayers implements DataStoreListener, ClockTimer.ClockListener {

    /** A media column's window, which shows the video of the clip in play. */
    public interface ColumnWindow {

        /** Adds a clip's video, not yet shown. */
        void addClip(Object clip, Component video);

        /** Removes a clip's video. */
        void removeClip(Object clip);

        /**
         * Shows a clip's video, or black for null or a clip without video, and the clip's volume.
         *
         * @param volume the clip's volume, from 0 to 1, or null when no clip is in play
         */
        void showClip(Object clip, Double volume);

        /** Calls the listener with the volume the user sets for the clip in play. */
        void setVolumeListener(DoubleConsumer listener);

        /** Calls the listener when the user closes the window. */
        void setCloseListener(Runnable listener);

        /** Shows or hides the window. */
        void setShowing(boolean showing);

        /** Closes the window for good. */
        void close();
    }

    /** Where a clip plays, as the clock's threads read it. */
    record Span(Cell clip, long onset, long end, StreamViewer player) {

        boolean holds(final long time) {
            return onset <= time && time < end;
        }
    }

    private final DataStore dataStore;
    private final VideoController videoController;
    private final Function<Variable, ColumnWindow> windowFactory;

    /** Runs window changes on the Swing EDT. */
    private final Consumer<Runnable> onEdt;

    /** The player of each clip that has one. */
    private final Map<Cell, StreamViewer> players = new HashMap<>();

    /** Clips whose players are opening, each with a token for the opening; a player whose token is gone is stale. */
    private final Map<Cell, Object> opening = new HashMap<>();

    /** Clips added in the app, which go if their file does not open. */
    private final Set<Cell> added = new HashSet<>();

    /** Columns added in the app for one clip, which go with it if its file does not open. */
    private final Set<Variable> addedColumns = new HashSet<>();

    /** Each media column's window, once a clip in it has a player. */
    private final Map<Variable, ColumnWindow> windows = new HashMap<>();

    private final Map<Variable, VariableListener> columnListeners = new HashMap<>();
    private final Map<Cell, CellListener> clipListeners = new HashMap<>();

    /** Each media column's clips, for the clock's threads. */
    private final Map<Variable, List<Span>> spans = new ConcurrentHashMap<>();

    /** The clip each column's window last showed; empty for black. */
    private final Map<Variable, Optional<Cell>> shown = new ConcurrentHashMap<>();

    /**
     * Starts following a spreadsheet's media columns, each in a {@link MediaWindow}. Their clips open with
     * {@link #openAll()}.
     *
     * @param dataStore       the spreadsheet
     * @param videoController what plays the clips
     */
    public MediaPlayers(final DataStore dataStore, final VideoController videoController) {
        this(dataStore, videoController,
            column -> new MediaWindow(Datavyu.getApplication().getMainFrame(), column.getName()),
            MediaPlayers::runOnEdt);
    }

    /**
     * @param windowFactory makes a media column's window
     * @param onEdt         runs window changes on the Swing EDT
     */
    MediaPlayers(final DataStore dataStore, final VideoController videoController,
                 final Function<Variable, ColumnWindow> windowFactory, final Consumer<Runnable> onEdt) {
        this.dataStore = dataStore;
        this.videoController = videoController;
        this.windowFactory = windowFactory;
        this.onEdt = onEdt;
        dataStore.addListener(this);
        for (Variable column : dataStore.getAllVariables()) {
            if (column.isMedia()) {
                follow(column);
            }
        }
        if (videoController.getClockTimer() != null) {
            videoController.getClockTimer().registerListener(this);
        }
    }

    /**
     * Opens a player for every clip whose file exists.
     *
     * @return the files of the clips that are missing
     */
    public List<String> openAll() {
        List<String> missing = new ArrayList<>();
        for (Variable column : dataStore.getAllVariables()) {
            if (column.isMedia()) {
                for (Cell clip : column.getCellsTemporally()) {
                    if (!open(clip)) {
                        missing.add(clip.getClip().file());
                    }
                }
            }
        }
        return missing;
    }

    /**
     * Adds media files to the spreadsheet, as the Add Media command and dropping files do. With one media column
     * selected, each file becomes a clip in it at the current time; otherwise each gets a new media column.
     *
     * @param files media files
     * @param time  the current time, where clips added to a selected column start
     * @throws UserWarningException if a column cannot be added
     */
    public void addMediaFiles(final List<File> files, final long time) throws UserWarningException {
        List<Variable> selected = dataStore.getSelectedVariables();
        for (File file : files) {
            if (selected.size() == 1 && selected.get(0).isMedia()) {
                addClip(selected.get(0), file, time);
            } else {
                addMedia(file);
            }
        }
    }

    /**
     * Adds a clip of a media file to a media column. If the file does not open, the clip goes again.
     *
     * @param column a media column
     * @param file   the media file
     * @param onset  where the clip starts
     * @return the clip
     */
    public Cell addClip(final Variable column, final File file, final long onset) {
        Cell clip = column.createClip(onset, MediaClip.of(file.getAbsolutePath()));
        added.add(clip);
        if (!open(clip)) {
            added.remove(clip);
            column.removeCell(clip);
        }
        return clip;
    }

    /**
     * Adds media to the spreadsheet: a new media column, named {@code media_#}, holding one clip of the file at time
     * zero. If the file does not open, the column goes again.
     *
     * @param file the media file
     * @return the new column
     * @throws UserWarningException if the column cannot be added
     */
    public Variable addMedia(final File file) throws UserWarningException {
        Set<String> names = dataStore.getAllVariables().stream().map(Variable::getName).collect(Collectors.toSet());
        Variable column = dataStore.createMediaVariable(MediaColumnNames.next(names));
        column.setOrderIndex(dataStore.getAllVariables().size() - 1);
        // Not selected, so that media added next gets its own column unless the user picks this one.
        column.setSelected(false);
        addedColumns.add(column);
        Cell clip = column.createClip(0, MediaClip.of(file.getAbsolutePath()));
        added.add(clip);
        if (!open(clip)) {
            added.remove(clip);
            addedColumns.remove(column);
            dataStore.removeVariable(column);
        }
        return column;
    }

    /** @return the player of a clip, or null if it has none */
    public StreamViewer getPlayer(final Cell clip) {
        return players.get(clip);
    }

    /**
     * Where playback stops by itself: at the end of the last media clip of known length, unless that is turned off
     * ({@link org.datavyu.util.Settings#isStopAtEndOfMedia}). Safe to call from the clock's threads.
     *
     * @param stopAtEndOfMedia whether playback stops at the end of the media
     * @return the time to stop at, or -1 to play on
     */
    public long stopTime(final boolean stopAtEndOfMedia) {
        if (!stopAtEndOfMedia) {
            return -1;
        }
        long end = -1;
        for (List<Span> clips : spans.values()) {
            for (Span span : clips) {
                if (span.end() > span.onset()) {
                    end = Math.max(end, span.end());
                }
            }
        }
        return end;
    }

    /**
     * Finds which clip of a column is in play: of the clips whose span holds the time, the one that starts latest,
     * and of those starting together, the last. A clip of unknown length plays nowhere.
     *
     * @param clips a column's clips
     * @param time  a time on the timeline
     * @return the clip in play, or null in a gap
     */
    static Cell inPlay(final List<Span> clips, final long time) {
        Span latest = null;
        for (Span span : clips) {
            if (span.holds(time) && (latest == null || span.onset() >= latest.onset())) {
                latest = span;
            }
        }
        return latest == null ? null : latest.clip();
    }

    /**
     * Puts each column's clip in play at a time, and the others out of play. Called from the clock's threads and the
     * EDT; the windows change on the EDT.
     */
    private void play(final long time) {
        for (Map.Entry<Variable, List<Span>> entry : spans.entrySet()) {
            Cell winner = inPlay(entry.getValue(), time);
            for (Span span : entry.getValue()) {
                if (span.player() != null) {
                    // A hidden column's clips are out of play: paused and silent.
                    span.player().setInPlay(span.clip() == winner && !entry.getKey().isHidden());
                }
            }
            Variable column = entry.getKey();
            Optional<Cell> now = Optional.ofNullable(winner);
            if (!now.equals(shown.put(column, now))) {
                onEdt.accept(() -> {
                    ColumnWindow window = windows.get(column);
                    if (window != null) {
                        window.showClip(winner, winner == null ? null : winner.getClip().volume());
                    }
                });
            }
        }
    }

    private static void runOnEdt(final Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    /** Takes a fresh snapshot of a column's clips, and puts the right one in play now. */
    private void refresh(final Variable column) {
        if (!columnListeners.containsKey(column)) {
            spans.remove(column);
            shown.remove(column);
            return;
        }
        List<Span> snapshot = new ArrayList<>();
        for (Cell clip : column.getCells()) {
            long duration = clip.getClip().duration();
            snapshot.add(new Span(clip, clip.getOnset(), duration > 0 ? clip.getOnset() + duration : clip.getOnset(),
                players.get(clip)));
        }
        spans.put(column, List.copyOf(snapshot));
        // The window may show a clip that has just gone.
        shown.remove(column);
        play(videoController.getCurrentTime());
    }

    /**
     * Opens a clip's player, unless it has one or is opening.
     *
     * @return false if the clip's file is missing
     */
    private boolean open(final Cell clip) {
        File file = new File(clip.getClip().file());
        if (!file.exists()) {
            return false;
        }
        if (players.containsKey(clip) || opening.containsKey(clip)) {
            return true;
        }
        Object token = new Object();
        opening.put(clip, token);
        Variable column = clip.getVariable();
        videoController.openStream(Identifier.generateIdentifier(), file, player -> {
            player.setWindowListener(showing -> setWindowShowing(column, showing));
            player.setOffset(clip.getOnset());
            player.setVolumeLevel((float) clip.getClip().volume());
        }, player -> {
            if (opening.get(clip) != token) {
                // The clip went, and perhaps came back with undo, while this player was opening.
                videoController.shutdown(player.getIdentifier(), false);
                return;
            }
            opening.remove(clip);
            added.remove(clip);
            addedColumns.remove(column);
            if (!column.contains(clip) || !columnListeners.containsKey(column)) {
                // The clip went while its player was opening.
                videoController.shutdown(player.getIdentifier(), false);
                return;
            }
            players.put(clip, player);
            windows.computeIfAbsent(column, this::newWindow).addClip(clip, player.getVideoComponent());
            // Media without video has no size or frame rate.
            Dimension size = player.getVideoSize();
            boolean video = size != null && size.width > 0 && size.height > 0;
            clip.setClip(clip.getClip().withDetails(Math.max(0, player.getDuration()),
                video ? exact(player.getFramesPerSecond()) : 0, video ? size.width : 0, video ? size.height : 0));
            refresh(column);
            player.setViewerVisible(!column.isHidden());
        }, () -> {
            if (opening.get(clip) != token) {
                return;
            }
            opening.remove(clip);
            if (added.remove(clip)) {
                if (addedColumns.remove(column)) {
                    dataStore.removeVariable(column);
                } else {
                    column.removeCell(clip);
                }
            }
        });
        return true;
    }

    private void close(final Cell clip) {
        opening.remove(clip);
        StreamViewer player = players.remove(clip);
        if (player != null) {
            Variable column = clip.getVariable();
            ColumnWindow window = windows.get(column);
            if (window != null) {
                window.removeClip(clip);
                if (players.keySet().stream().noneMatch(other -> other.getVariable() == column)) {
                    windows.remove(column).close();
                }
            }
            videoController.shutdown(player.getIdentifier(), false);
        }
    }

    /** Makes a column's window, whose volume control sets the volume of the clip in play. */
    private ColumnWindow newWindow(final Variable column) {
        ColumnWindow window = windowFactory.apply(column);
        // Closing a media window hides its column, which silences it; showing the column shows the window again.
        window.setCloseListener(() -> column.setHidden(true));
        window.setVolumeListener(volume -> {
            Cell clip = shown.getOrDefault(column, Optional.empty()).orElse(null);
            if (clip != null && column.contains(clip)) {
                setVolume(clip, volume);
            }
        });
        return window;
    }

    /**
     * Sets a clip's volume, as its column's window does for the clip in play.
     *
     * @param clip   a media clip
     * @param volume from 0 to 1
     */
    public void setVolume(final Cell clip, final double volume) {
        clip.setClip(clip.getClip().withVolume(volume));
        StreamViewer player = players.get(clip);
        if (player != null) {
            player.setVolumeLevel((float) volume);
        }
    }

    /** Shows or hides a column's window; a hidden column's window stays hidden. */
    private void setWindowShowing(final Variable column, final boolean showing) {
        onEdt.accept(() -> {
            ColumnWindow window = windows.get(column);
            if (window != null) {
                window.setShowing(showing && !column.isHidden());
            }
        });
    }

    private void follow(final Variable column) {
        VariableListener listener = new VariableListener() {
            @Override
            public void nameChanged(final String newName) {
            }

            @Override
            public void visibilityChanged(final boolean isHidden) {
                for (Cell clip : column.getCells()) {
                    StreamViewer player = players.get(clip);
                    if (player != null) {
                        player.setViewerVisible(!isHidden);
                    }
                }
                refresh(column);
            }

            @Override
            public void cellInserted(final Cell clip) {
                followClip(clip);
                open(clip);
                refresh(column);
            }

            @Override
            public void cellRemoved(final Cell clip) {
                unfollowClip(clip);
                close(clip);
                refresh(column);
            }
        };
        columnListeners.put(column, listener);
        column.addListener(listener);
        column.getCells().forEach(this::followClip);
        refresh(column);
    }

    private void unfollow(final Variable column) {
        VariableListener listener = columnListeners.remove(column);
        if (listener != null) {
            column.removeListener(listener);
        }
        for (Cell clip : column.getCells()) {
            unfollowClip(clip);
            close(clip);
        }
        refresh(column);
    }

    private void followClip(final Cell clip) {
        CellListener listener = new CellListener() {
            @Override
            public void offsetChanged(final long newOffset) {
                refresh(clip.getVariable());
            }

            @Override
            public void onsetChanged(final long newOnset) {
                StreamViewer player = players.get(clip);
                if (player != null && player.getOffset() != newOnset) {
                    player.setOffset(newOnset);
                    videoController.updateMaxViewerDuration();
                }
                refresh(clip.getVariable());
            }

            @Override
            public void highlightingChange(final boolean isHighlighted) {
            }

            @Override
            public void selectionChange(final boolean isSelected) {
            }

            @Override
            public void valueChange(final CellValue newCellValue) {
            }
        };
        clipListeners.put(clip, listener);
        clip.addListener(listener);
    }

    private void unfollowClip(final Cell clip) {
        CellListener listener = clipListeners.remove(clip);
        if (listener != null) {
            clip.removeListener(listener);
        }
    }

    @Override
    public void variableAdded(final Variable column) {
        if (column.isMedia()) {
            follow(column);
            column.getCells().forEach(this::open);
        }
    }

    @Override
    public void variableRemoved(final Variable column) {
        if (column.isMedia()) {
            unfollow(column);
        }
    }

    @Override
    public void variableOrderChanged() {
    }

    @Override
    public void variableHidden(final Variable column) {
    }

    @Override
    public void variableVisible(final Variable column) {
    }

    @Override
    public void variableNameChange(final Variable column) {
    }

    @Override
    public void clockSeekPlayback(final double clockTime) {
        play((long) clockTime);
    }

    @Override
    public void clockBoundaryCheck(final double clockTime) {
        play((long) clockTime);
    }

    @Override
    public void streamsBoundaryCheck(final double clockTime) {
        play((long) clockTime);
    }

    @Override
    public void clockForceSync(final double clockTime) {
        play((long) clockTime);
    }

    @Override
    public void clockPeriodicSync(final double clockTime) {
        play((long) clockTime);
    }

    @Override
    public void clockStart(final double clockTime) {
        play((long) clockTime);
    }

    @Override
    public void clockStop(final double clockTime) {
        play((long) clockTime);
    }

    @Override
    public void clockPause(final double clockTime) {
        play((long) clockTime);
    }

    @Override
    public void clockRate(final float rate) {
    }

    /** @return the float as written in decimal, so that 0.7f is saved as 0.7 rather than 0.699999988079071 */
    static double exact(final float value) {
        return Double.parseDouble(Float.toString(value));
    }
}

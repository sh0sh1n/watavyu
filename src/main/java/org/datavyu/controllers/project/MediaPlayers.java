package org.datavyu.controllers.project;

import org.datavyu.Datavyu;
import org.datavyu.models.Identifier;
import org.datavyu.models.component.TrackModel;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plays the spreadsheet's media columns. The spreadsheet holds each column and its clips; this opens a player for each
 * clip whose file exists, and keeps the players and the spreadsheet in step: a clip added, moved or removed in the
 * spreadsheet (by editing, undo or opening a project) opens, moves or closes its player, and a track dragged, locked or
 * removed in the video controller moves, locks or removes its clip. A clip whose file is missing keeps its place and
 * what is known about it, and has no player.
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

        /** Shows a clip's video, or black for null or a clip without video. */
        void showClip(Object clip);

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

    /** Clips whose players are opening. */
    private final Set<Cell> opening = new HashSet<>();

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
     * Copies what only the players know into their clips: their volume, and whether their tracks are locked.
     */
    public void updateClips() {
        for (Map.Entry<Cell, StreamViewer> entry : players.entrySet()) {
            Cell clip = entry.getKey();
            clip.setClip(clip.getClip().withVolume(exact(entry.getValue().getVolumeLevel())));
        }
        syncLocks();
    }

    /** Locks each media column whose track is locked, and unlocks the rest. */
    public void syncLocks() {
        for (Map.Entry<Cell, StreamViewer> entry : players.entrySet()) {
            TrackModel track = videoController.getMixerController().getTrackModel(entry.getValue().getIdentifier());
            if (track != null) {
                entry.getKey().getVariable().setLocked(track.isLocked());
            }
        }
    }

    /**
     * A track was dragged in the video controller: its clip moves with it.
     *
     * @param player the track's player
     * @param onset  where the track now starts
     */
    public void playerMoved(final Identifier player, final long onset) {
        Cell clip = clipOf(player);
        if (clip != null && clip.getOnset() != onset) {
            clip.setOnset(onset);
        }
    }

    /**
     * A track was removed in the video controller: its clip goes, and its column with it once empty.
     *
     * @param player the track's player
     */
    public void playerRemoved(final Identifier player) {
        Cell clip = clipOf(player);
        if (clip == null) {
            return;
        }
        Variable column = clip.getVariable();
        column.removeCell(clip);
        if (column.getCells().isEmpty()) {
            dataStore.removeVariable(column);
        }
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
                    span.player().setInPlay(span.clip() == winner);
                }
            }
            Variable column = entry.getKey();
            Optional<Cell> now = Optional.ofNullable(winner);
            if (!now.equals(shown.put(column, now))) {
                onEdt.accept(() -> {
                    ColumnWindow window = windows.get(column);
                    if (window != null) {
                        window.showClip(winner);
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

    private Cell clipOf(final Identifier player) {
        for (Map.Entry<Cell, StreamViewer> entry : players.entrySet()) {
            if (entry.getValue().getIdentifier().equals(player)) {
                return entry.getKey();
            }
        }
        return null;
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
        if (players.containsKey(clip) || !opening.add(clip)) {
            return true;
        }
        Variable column = clip.getVariable();
        videoController.openStream(Identifier.generateIdentifier(), file, player -> {
            player.setWindowListener(showing -> setWindowShowing(column, showing));
            player.setOffset(clip.getOnset());
            player.setVolumeLevel((float) clip.getClip().volume());
        }, player -> {
            opening.remove(clip);
            added.remove(clip);
            addedColumns.remove(column);
            if (!column.contains(clip) || !columnListeners.containsKey(column)) {
                // The clip went while its player was opening.
                videoController.shutdown(player.getIdentifier(), false);
                return;
            }
            players.put(clip, player);
            videoController.getMixerController().setTrackLocked(player.getIdentifier(), column.isLocked());
            windows.computeIfAbsent(column, windowFactory).addClip(clip, player.getVideoComponent());
            Dimension size = player.getVideoSize();
            clip.setClip(clip.getClip().withDetails(Math.max(0, player.getDuration()),
                exact(player.getFramesPerSecond()), size == null ? 0 : size.width, size == null ? 0 : size.height));
            refresh(column);
            player.setViewerVisible(!column.isHidden());
        }, () -> {
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
                    videoController.getMixerController().getTracksEditorController()
                        .setTrackOffset(player.getIdentifier(), newOnset);
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

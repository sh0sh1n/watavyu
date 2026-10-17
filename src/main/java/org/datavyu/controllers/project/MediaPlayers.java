package org.datavyu.controllers.project;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.datavyu.views.VideoController;

import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plays the spreadsheet's media columns. The spreadsheet holds each column and its clips; this opens a player for each
 * clip whose file exists, and keeps the players and the spreadsheet in step: a clip added, moved or removed in the
 * spreadsheet (by editing, undo or opening a project) opens, moves or closes its player, and a track dragged, locked or
 * removed in the video controller moves, locks or removes its clip. A clip whose file is missing keeps its place and
 * what is known about it, and has no player.
 *
 * <p>Until F4's later steps, each clip has its own player, window and track. Everything here runs on the Swing EDT.
 */
public final class MediaPlayers implements DataStoreListener {

    private static final Logger logger = LogManager.getLogger(MediaPlayers.class);

    private final DataStore dataStore;
    private final VideoController videoController;

    /** The player of each clip that has one. */
    private final Map<Cell, StreamViewer> players = new HashMap<>();

    /** Clips whose players are opening. */
    private final Set<Cell> opening = new HashSet<>();

    /** Clips added in the app, whose column goes if their file does not open. */
    private final Set<Cell> added = new HashSet<>();

    private final Map<Variable, VariableListener> columnListeners = new HashMap<>();
    private final Map<Cell, CellListener> clipListeners = new HashMap<>();

    /**
     * Starts following a spreadsheet's media columns. Their clips open with {@link #openAll()}.
     *
     * @param dataStore       the spreadsheet
     * @param videoController what plays the clips
     */
    public MediaPlayers(final DataStore dataStore, final VideoController videoController) {
        this.dataStore = dataStore;
        this.videoController = videoController;
        dataStore.addListener(this);
        for (Variable column : dataStore.getAllVariables()) {
            if (column.isMedia()) {
                follow(column);
            }
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
        Cell clip = column.createClip(0, MediaClip.of(file.getAbsolutePath()));
        added.add(clip);
        if (!open(clip)) {
            added.remove(clip);
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
        players.remove(clip);
        Variable column = clip.getVariable();
        column.removeCell(clip);
        if (column.getCells().isEmpty()) {
            dataStore.removeVariable(column);
        }
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
            player.setOffset(clip.getOnset());
            player.setVolumeLevel((float) clip.getClip().volume());
            player.setViewerVisible(!column.isHidden());
        }, player -> {
            opening.remove(clip);
            added.remove(clip);
            if (!column.contains(clip) || !columnListeners.containsKey(column)) {
                // The clip went while its player was opening.
                videoController.shutdown(player.getIdentifier(), false);
                return;
            }
            players.put(clip, player);
            videoController.getMixerController().setTrackLocked(player.getIdentifier(), column.isLocked());
            Dimension size = player.getVideoSize();
            clip.setClip(clip.getClip().withDetails(Math.max(0, player.getDuration()),
                exact(player.getFramesPerSecond()), size == null ? 0 : size.width, size == null ? 0 : size.height));
            showWindow(player);
        }, () -> {
            opening.remove(clip);
            if (added.remove(clip) && column.getCells().size() == 1) {
                dataStore.removeVariable(column);
            }
        });
        return true;
    }

    private void close(final Cell clip) {
        opening.remove(clip);
        StreamViewer player = players.remove(clip);
        if (player != null) {
            videoController.shutdown(player.getIdentifier(), false);
        }
    }

    private void follow(final Variable column) {
        VariableListener listener = new VariableListener() {
            @Override
            public void nameChanged(final String newName) {
            }

            @Override
            public void visibilityChanged(final boolean isHidden) {
                setVisible(column, !isHidden);
            }

            @Override
            public void cellInserted(final Cell clip) {
                followClip(clip);
                open(clip);
            }

            @Override
            public void cellRemoved(final Cell clip) {
                unfollowClip(clip);
                close(clip);
            }
        };
        columnListeners.put(column, listener);
        column.addListener(listener);
        column.getCells().forEach(this::followClip);
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
    }

    private void followClip(final Cell clip) {
        CellListener listener = new CellListener() {
            @Override
            public void offsetChanged(final long newOffset) {
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

    private void setVisible(final Variable column, final boolean visible) {
        for (Cell clip : column.getCells()) {
            StreamViewer player = players.get(clip);
            if (player != null) {
                player.setViewerVisible(visible);
            }
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

    /** Puts a media window where it was last left, keeping it hidden if it was hidden. */
    private static void showWindow(final StreamViewer player) {
        if (player.getParentJDialog() != null && Datavyu.getApplication() != null) {
            boolean visible = player.getParentJDialog().isVisible();
            Datavyu.getApplication().show(player.getParentJDialog());
            if (!visible) {
                player.getParentJDialog().setVisible(false);
            }
        } else {
            logger.debug("No window to show for " + player.getIdentifier());
        }
    }

    /** @return the float as written in decimal, so that 0.7f is saved as 0.7 rather than 0.699999988079071 */
    static double exact(final float value) {
        return Double.parseDouble(Float.toString(value));
    }
}

package org.datavyu.controllers.project;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.datavyu.undoableedits.AddCellEdit;
import org.datavyu.undoableedits.AddColumnEdit;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

/**
 * The commands that add media to one spreadsheet. Each file added is an edit in the undo history, so undo takes the
 * clip or column out again and closes its player. {@link MediaPlayers} opens the players; a file that does not open is
 * taken out again, and its edit then changes nothing.
 */
public final class MediaCommands {

    private static final Logger logger = LogManager.getLogger(MediaCommands.class);

    private final ProjectController spreadsheet;

    /**
     * @param spreadsheet the spreadsheet media is added to
     */
    MediaCommands(final ProjectController spreadsheet) {
        this.spreadsheet = spreadsheet;
    }

    /**
     * Add Media, from the Spreadsheet menu and dropping media files on the spreadsheet. With one media column selected,
     * each file becomes a clip in it at the time; otherwise each gets a new media column, holding it at time zero. A
     * locked selected column refuses them, and {@code intoNewColumns} is asked once whether they go in new columns
     * instead. Each file added is an edit of its own.
     *
     * @param files          media files
     * @param time           the current time, where clips added to a selected column start
     * @param intoNewColumns given the locked column, whether to add the files to new columns instead
     * @throws UserWarningException if a column cannot be added
     */
    public void addMedia(final List<File> files, final long time, final Predicate<Variable> intoNewColumns)
            throws UserWarningException {
        if (players() == null) {
            return;
        }
        Variable target = selectedMediaColumn();
        if (target != null && target.refusesEdits()) {
            if (!intoNewColumns.test(target)) {
                return;
            }
            target = null;
        }
        for (File file : files) {
            logger.info("Add media " + file);
            if (target == null) {
                addInNewColumn(file);
            } else {
                addClip(target, file, time);
            }
        }
    }

    /** @return the one selected column, if it is a media column, or null */
    private Variable selectedMediaColumn() {
        List<Variable> selected = spreadsheet.getDataStore().getSelectedVariables();
        return selected.size() == 1 && selected.get(0).isMedia() ? selected.get(0) : null;
    }

    private MediaPlayers players() {
        return spreadsheet.getVideoController().getMediaPlayers();
    }

    /** Adds a clip to a column, as an edit unless its file failed to open at once. */
    private void addClip(final Variable column, final File file, final long time) {
        Cell clip = players().addClip(column, file, time);
        if (spreadsheet.getDataStore().getVariable(clip) != null) {
            spreadsheet.postEdit(new AddCellEdit(spreadsheet, column.getName(), clip));
        }
    }

    /** Adds a media column holding a clip of the file, as an edit unless the file failed to open at once. */
    private void addInNewColumn(final File file) throws UserWarningException {
        DataStore dataStore = spreadsheet.getDataStore();
        Variable column = players().addMedia(file);
        if (dataStore.contains(column)) {
            spreadsheet.postEdit(new AddColumnEdit(dataStore, column));
        }
    }
}

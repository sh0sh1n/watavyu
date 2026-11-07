package com.sh0sh1n.watavyu.controllers.project;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.models.db.Cell;
import com.sh0sh1n.watavyu.models.db.UserWarningException;
import com.sh0sh1n.watavyu.models.db.Column;
import com.sh0sh1n.watavyu.undoableedits.AddCellEdit;
import com.sh0sh1n.watavyu.undoableedits.AddColumnEdit;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

/**
 * The commands that add media to one spreadsheet. Each file added is an edit in the undo history, so undo takes the
 * clip or column out again and closes its player. {@link MediaPlayers} opens the players; a file that does not open
 * stays, marked as such (owner, 2026-11-03), for the user to undo or delete.
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
    public void addMedia(final List<File> files, final long time, final Predicate<Column> intoNewColumns)
            throws UserWarningException {
        // No players until the spreadsheet's view is made.
        if (spreadsheet.getVideoController() == null || players() == null) {
            return;
        }
        Column target = selectedMediaColumn();
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
    private Column selectedMediaColumn() {
        List<Column> selected = spreadsheet.getDataStore().getSelectedColumns();
        return selected.size() == 1 && selected.get(0).isMedia() ? selected.get(0) : null;
    }

    private MediaPlayers players() {
        return spreadsheet.getVideoController().getMediaPlayers();
    }

    private void addClip(final Column column, final File file, final long time) {
        Cell clip = players().addClip(column, file, time);
        spreadsheet.postEdit(new AddCellEdit(spreadsheet, column.getName(), clip));
    }

    private void addInNewColumn(final File file) throws UserWarningException {
        Column column = players().addMedia(file);
        spreadsheet.postEdit(new AddColumnEdit(spreadsheet.getDataStore(), column));
    }
}

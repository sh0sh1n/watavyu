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
package com.sh0sh1n.watavyu.controllers;

import com.sh0sh1n.watavyu.models.UserWarningException;
import com.sh0sh1n.watavyu.projectfile.Fingerprint;
import com.sh0sh1n.watavyu.projectfile.ProjectFileException;
import com.sh0sh1n.watavyu.projectfile.ProjectJson;
import com.sh0sh1n.watavyu.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Autosaves one spreadsheet: every interval, its unsaved work is saved to its one autosave ({@link Autosave}) in the
 * autosave folder, replacing what was there. Each spreadsheet has its own, so every open tab can be recovered after a
 * crash. Only unsaved work is kept: a spreadsheet with no unsaved changes has no autosave, and the autosave is written
 * only when what it would hold differs from what it holds.
 *
 * <p>The autosave goes only when its work is saved or knowingly discarded ({@link #clear}, {@link #discard}). Quitting
 * any other way, a crash or a {@code kill}, leaves it for the next launch to recover.
 */
public final class AutoSaveController implements ActionListener {

    /** Logger for this class */
    private static final Logger logger = LogManager.getLogger(AutoSaveController.class);

    /** The spreadsheet saved */
    private final ProjectController controller;

    /** Where autosaves are kept */
    private final Path folder;

    /** Runs the autosaves, on the EDT */
    private final Timer timer;

    /** The spreadsheet's autosave, or null while it has none */
    private Autosave autosave;

    /**
     * Makes a stopped autosave.
     *
     * @param controller the spreadsheet to save
     * @param minutes    how often to save it; at least 1
     * @param folder     where autosaves are kept
     */
    public AutoSaveController(final ProjectController controller, final int minutes, final Path folder) {
        if (minutes < 1) {
            throw new IllegalArgumentException("Autosave interval must be at least a minute: " + minutes);
        }
        this.controller = controller;
        this.folder = folder;
        timer = new Timer(minutes * 60_000, this);
    }

    /** Returns where autosaves are kept: {@code Autosaves} in the folder that holds the settings. */
    public static Path defaultFolder() {
        return Settings.directory().resolve("Autosaves");
    }

    /** Starts saving, the first time after one interval. */
    public void start() {
        timer.start();
    }

    /** Stops saving and deletes the autosave, as the spreadsheet's work was saved or knowingly discarded. */
    public void discard() {
        timer.stop();
        clear();
    }

    /** Deletes the autosave, as the spreadsheet's work was just saved; saving goes on while it changes. */
    public void clear() {
        if (autosave != null) {
            autosave.delete();
            autosave = null;
        }
    }

    /**
     * Takes over an autosave recovered at launch, as the spreadsheet holds its work: it is replaced as the spreadsheet
     * changes, rather than a second autosave being made.
     *
     * @param recovered the autosave the spreadsheet was opened from
     */
    public void adopt(final Autosave recovered) {
        clear();
        autosave = recovered;
    }

    /** Returns the spreadsheet's autosave, or null while it has none. */
    Autosave getAutosave() {
        return autosave;
    }

    @Override
    public void actionPerformed(final ActionEvent evt) {
        save();
    }

    /**
     * Saves the spreadsheet's unsaved work to its autosave, unless the autosave holds it already. Once the spreadsheet
     * has no unsaved changes, as after saving it, its autosave is deleted instead.
     */
    void save() {
        if (!controller.isChanged()) {
            clear();
            return;
        }
        try {
            String project = ProjectJson.toJson(controller.toDocument(folder));
            if (autosave != null && Fingerprint.of(project).equals(autosave.getContents())) {
                return;
            }
            if (autosave == null) {
                autosave = Autosave.create(folder);
            }
            File projectFile = controller.getProjectFile();
            autosave.write(project, projectFile == null ? null
                : new Autosave.Origin(projectFile, controller.getSavedFingerprint()));
        } catch (UserWarningException | ProjectFileException | IOException e) {
            logger.error("Unable to autosave " + controller.getProjectNamePretty(), e);
        }
    }
}

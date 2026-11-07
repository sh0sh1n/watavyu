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
package org.datavyu.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.projectfile.ProjectDocument;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

/**
 * Autosaves one spreadsheet: every interval, its project is saved to a project file in the temporary folder, replacing
 * the one saved there before. Each spreadsheet has its own, so every open tab can be recovered after a crash. Only
 * unsaved work is kept: a spreadsheet with no unsaved changes has no autosave, so recovery offers only work that would
 * otherwise be lost.
 */
public final class AutoSaveController implements ActionListener {

    /** Logger for this class */
    private static final Logger logger = LogManager.getLogger(AutoSaveController.class);

    /** The spreadsheet saved */
    private final ProjectController controller;

    /** Runs the autosaves, on the EDT */
    private final Timer timer;

    /** The last autosave written, or null before the first */
    private File file;

    /**
     * Makes a stopped autosave.
     *
     * @param controller the spreadsheet to save
     * @param minutes    how often to save it; at least 1
     */
    public AutoSaveController(final ProjectController controller, final int minutes) {
        if (minutes < 1) {
            throw new IllegalArgumentException("Autosave interval must be at least a minute: " + minutes);
        }
        this.controller = controller;
        timer = new Timer(minutes * 60_000, this);
    }

    /** Starts saving, the first time after one interval. */
    public void start() {
        timer.start();
    }

    /** Stops saving and deletes the last autosave, as the spreadsheet's work was saved or knowingly discarded. */
    public void discard() {
        timer.stop();
        clear();
    }

    /** Deletes the last autosave, as the spreadsheet's work was just saved; saving goes on while it changes. */
    public void clear() {
        if (file != null) {
            delete(file);
            file = null;
        }
    }

    /** Returns the last autosave written, or null if there is none. */
    File getFile() {
        return file;
    }

    @Override
    public void actionPerformed(final ActionEvent evt) {
        save();
    }

    /**
     * Saves the spreadsheet's project to a project file in the temporary folder, replacing the last one. Once the
     * spreadsheet has no unsaved changes, as after saving it, its last autosave is deleted instead.
     */
    void save() {
        if (!controller.isChanged()) {
            clear();
            return;
        }
        File next = null;
        try {
            String name = controller.getProjectName() == null ? "noname" : controller.getProjectName();
            next = File.createTempFile("~" + name + "_", ProjectDocument.EXTENSION);
            next.deleteOnExit();
            new SaveController().saveProject(next, controller.toDocument(next.getParentFile().toPath()), false);
            // The previous autosave goes only once this one is written, so a failure or crash leaves one behind.
            if (file != null) {
                delete(file);
            }
            file = next;
            next = null;
        } catch (UserWarningException lee) {
            logger.error("UserWarningException: Unable to autosave.", lee);
        } catch (IOException ioe) {
            logger.error("IOException: Unable to autosave.", ioe);
        } finally {
            if (next != null) {
                delete(next);
            }
        }
    }

    /** Deletes an autosave, and logs it if the file stays. */
    private static void delete(final File autosave) {
        if (!autosave.delete() && autosave.exists()) {
            logger.warn("Could not delete the autosave " + autosave);
        }
    }
}

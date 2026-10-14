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
package org.datavyu.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.projectfile.ProjectDocument;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

public class AutoSaveController implements ActionListener {

    /** Logger for this class */
    private static Logger logger = LogManager.getLogger(AutoSaveController.class);

    /** Timer for this class */
    private static Timer timer;

    /** File to auto save */
    private static File file;

    private AutoSaveController() {}

    public static void setInterval(int interval) {
        if (interval == 0) {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            return;
        }
        interval *= 60000;
        if (timer == null) {
            timer = new Timer(interval, new AutoSaveController());
            timer.start();
        } else {
            timer.setDelay(interval);
        }
    }

    public static void stop() {
        if (timer != null) {
            timer.stop();
        }
    }

    /** Saves the current project to a project file in the temporary folder, replacing the last one saved there. */
    @Override
    public void actionPerformed(ActionEvent evt) {
        try {
            if (file != null) {
                file.delete();
            }
            ProjectController projController = Datavyu.getProjectController();
            String name = projController.getProjectName() == null ? "noname" : projController.getProjectName();
            file = File.createTempFile("~" + name + "_", ProjectDocument.EXTENSION);
            file.deleteOnExit();
            new SaveController().saveProject(file, projController.toDocument(file.getParentFile().toPath()), false);
        } catch (UserWarningException lee) {
            logger.error("UserWarningException: Unable to autosave.", lee);
        } catch (IOException ioe) {
            logger.error("IOException: Unable to autosave.", ioe);
        }
    }
}

/**
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.controllers;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Imports Datavyu's JSON export into a spreadsheet: each pass becomes an annotation column.
 *
 * <p>The whole file is read ({@link DatavyuJsonReader}) before anything is added, so a file with any part Watavyu
 * cannot read adds nothing, and the message says where.
 */
public final class OpenDataStoreFileController {

    private static final Logger logger = LogManager.getLogger(OpenDataStoreFileController.class);

    /** Shows the user the passes that were left out because the spreadsheet already has columns of their names. */
    private final Consumer<String> warnings;

    /**
     * @param warnings shows the user the passes that were left out because the spreadsheet already has their names
     */
    public OpenDataStoreFileController(final Consumer<String> warnings) {
        this.warnings = warnings;
    }

    /**
     * Adds the passes of a Datavyu JSON export to a spreadsheet as annotation columns. A pass whose name the
     * spreadsheet already has is left out, and the user is told. A file with any part Watavyu cannot read, such as a
     * negative time, adds nothing.
     *
     * @param file      the JSON file
     * @param dataStore the spreadsheet to add them to
     * @throws UserWarningException if any part of the file cannot be read, saying which
     * @throws IOException          if the file cannot be read
     */
    public void importJSONToSpreadsheet(final File file, final DataStore dataStore)
            throws UserWarningException, IOException {
        List<DatavyuJsonReader.Pass> passes;
        try (JsonParser parser = new JsonFactory().createParser(file)) {
            passes = DatavyuJsonReader.read(parser);
        } catch (DatavyuJsonReader.UnimportableException e) {
            String message = "Cannot import " + file.getName() + ": " + e.getMessage();
            logger.warn(message);
            throw new UserWarningException(message, e);
        }

        List<String> leftOut = new ArrayList<>();
        for (DatavyuJsonReader.Pass pass : passes) {
            if (dataStore.getVariable(pass.name()) == null) {
                add(pass, dataStore);
            } else {
                leftOut.add("\"" + pass.name() + "\"");
            }
        }
        if (!leftOut.isEmpty()) {
            warnings.accept("Left out the columns the spreadsheet already has: " + String.join(", ", leftOut) + ".");
        }
    }

    private static void add(final DatavyuJsonReader.Pass pass, final DataStore dataStore) throws UserWarningException {
        Variable column = dataStore.createVariable(pass.name(), true);
        column.setCodes(pass.codes());
        for (DatavyuJsonReader.PassCell passCell : pass.cells()) {
            Cell cell = column.createCell();
            cell.setOnset(passCell.onset());
            cell.setOffset(passCell.offset());
            cell.setValues(passCell.values());
        }
    }
}

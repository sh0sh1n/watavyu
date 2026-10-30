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

import org.datavyu.util.Resources;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.models.db.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


/**
 * Controller for saving the database to disk.
 */
// TODO: Use StringBuilder for various strings that are put together in methods here!
public final class ExportDatabaseFileController {

    /** Logger for this class */
    private static Logger logger = LogManager.getLogger(ExportDatabaseFileController.class);

    /** @return the spreadsheet's annotation columns, which are what is exported; media columns are not */
    private static List<Variable> annotationColumns(final DataStore dataStore) {
        List<Variable> columns = new ArrayList<>(dataStore.getAllVariables());
        columns.removeIf(Variable::isMedia);
        return columns;
    }

    /**
     * Save a Datavyu Spreadseet in a JSON File.
     *
     * @param dbFileName Target File
     * @param dataStore DataStore to be saved as JSON
     */

    public void exportAsJSON(String dbFileName, DataStore dataStore) throws UserWarningException{
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory f = mapper.getFactory();
        File jsonFile = new File(dbFileName);
        try {
            JsonGenerator g = f.createGenerator(jsonFile, JsonEncoding.UTF8);
            g.setPrettyPrinter(new DefaultPrettyPrinter());
            //Start a Spreadsheet Object
            g.writeStartObject();
            //Spreadsheet name Field
//            g.writeStringField("name", dataStore.getName());

            //Start an Array of Passes (Column(Spreadsheet)/Variable(DataStore))
            g.writeArrayFieldStart("passes");
            for (Variable column : annotationColumns(dataStore)) {
                // Start an Object for each Pass(Column/Variable)
                g.writeStartObject();
                // Pass(Column/Variable) name
                g.writeStringField("name", column.getName());

                // Datavyu's names: every column is a matrix of nominal codes.
                g.writeStringField("type", "MATRIX");

                g.writeObjectFieldStart("arguments");

                column.getRootNode().childArguments.forEach(argument -> {
                    try {
                        g.writeStringField(argument.name, "NOMINAL");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                g.writeEndObject();

                //Start an Array of Cells
                g.writeArrayFieldStart("cells");
                int count = 1;
                for (Cell cell : column.getCellsTemporally()) {
                    // Start an Object for each Cell
                    g.writeStartObject();

                    g.writeNumberField("id", count);
                    g.writeStringField("onset", cell.getOnsetString());
                    g.writeStringField("offset", cell.getOffsetString());
                    g.writeArrayFieldStart("values");

                    for (int k = 0; k < column.getRootNode().childArguments.size(); k++) {
                        g.writeString(cell.getMatrixValue(k).toString());
                    }

                    g.writeEndArray();
                    // End Cell Object
                    g.writeEndObject();
                    count++;
                }
                // End Cells Array
                g.writeEndArray();
                // End Pass Object
                g.writeEndObject();
            }
            // End the Passes Array
            g.writeEndArray();

            //End a Spreadsheet Object
            g.writeEndObject();

            g.close();
            logger.info("JSON File has been successfully saved");

        } catch (IOException e) {
            logger.error("Export as JSON failed. Error: ", e);
            Resources rMap = Resources.of(Datavyu.class);
            throw new UserWarningException(rMap.getString("UnableToSave.message", dbFileName), e);
        }

    }
}

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
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.models.db.*;
import org.datavyu.util.Timestamp;
import org.datavyu.views.discrete.SpreadSheetPanel;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports Datavyu's JSON export into a spreadsheet: each pass becomes an annotation column.
 */
public final class OpenDataStoreFileController {

    /** The logger instance for this class */
    private static Logger logger = LogManager.getLogger(OpenDataStoreFileController.class);

    /**
     * The one code of an annotation column converted from a Datavyu text or nominal column, named as Datavyu named
     * that column's value.
     */
    static final String VALUE_CODE = "var";

    /**
     * Datavyu's column types. Watavyu has only annotation columns (Datavyu's matrix columns): a text or nominal column
     * opens as an annotation column with one code, {@value #VALUE_CODE}, holding its values.
     */
    private enum ColumnKind {
        TEXT,
        NOMINAL,
        MATRIX
    }

    /**
     * Adds the passes of a Datavyu JSON export to a spreadsheet as annotation columns, leaving out any whose name the
     * spreadsheet already has. A file with a time Watavyu cannot read, such as a negative one, adds nothing.
     *
     * @param file        the JSON file
     * @param spreadSheet the spreadsheet to add them to
     * @throws UserWarningException if a column cannot be made, or a time cannot be read
     * @throws JsonParseException   if the file is not JSON
     * @throws IOException          if the file cannot be read
     */
    public void importJSONToSpreadsheet(File file, SpreadSheetPanel spreadSheet) throws UserWarningException,  JsonParseException, IOException {
        DataStore dataStore = spreadSheet.getDataStore();
        boolean changedBefore = dataStore.isChanged();
        List<Variable> added = new ArrayList<>();
        try {
            importPasses(file, spreadSheet, added);
        } catch (UnreadableTimeException e) {
            for (Variable column : added) {
                if (dataStore.getAllVariables().contains(column)) {
                    dataStore.removeVariable(column);
                }
            }
            if (!changedBefore) {
                dataStore.markAsUnchanged();
            }
            String message = "Cannot import " + file.getName() + ": " + e.getMessage();
            logger.warn(message);
            throw new UserWarningException(message);
        }
    }

    /** A time in the file that {@link Timestamp#parse} refuses, described for the user. */
    private static final class UnreadableTimeException extends Exception {
        UnreadableTimeException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Reads a cell's onset or offset.
     *
     * @param parser     the parser, on the time's value
     * @param field      {@code onset} or {@code offset}
     * @param column     the cell's column
     * @param cellNumber the cell's place in its column, from 1
     * @throws UnreadableTimeException if the value is not a time, or is negative
     */
    private static long readTime(final JsonParser parser, final String field, final String column,
                                 final int cellNumber) throws IOException, UnreadableTimeException {
        String text = parser.getValueAsString();
        try {
            return Timestamp.parse(text);
        } catch (IllegalArgumentException e) {
            throw new UnreadableTimeException("cell " + cellNumber + " of column \"" + column + "\" has the " + field
                    + " " + (text == null ? "null" : "\"" + text + "\"") + " (line "
                    + parser.currentLocation().getLineNr() + "), which is not a time. Times are HH:MM:SS:mmm and "
                    + "never negative. Nothing was imported.", e);
        }
    }

    private void importPasses(File file, SpreadSheetPanel spreadSheet, List<Variable> added)
            throws UserWarningException, IOException, UnreadableTimeException {
        if (file.getAbsolutePath().endsWith(".json")) {
            JsonFactory factory = new JsonFactory();
            try (JsonParser parser = factory.createParser(file)) {
            while (!parser.isClosed()) {
                JsonToken token = parser.nextToken();
                if (token == null) { break; }

                if (JsonToken.START_OBJECT.equals(token)) {

                    token = parser.nextToken();
                    if (JsonToken.FIELD_NAME.equals(token) && "passes".equals(parser.currentName())) {

                        DataStore dataStore = spreadSheet.getDataStore();

                        token = parser.nextToken();
                        if (!JsonToken.START_ARRAY.equals(token)) { break; }

                        while (!JsonToken.END_ARRAY.equals(token)) { // for each pass

                            token = parser.nextToken();
                            if (token == null) { break; }
                            if (!JsonToken.START_OBJECT.equals(token)) { break; }

                            token = parser.nextToken();
                            if (JsonToken.FIELD_NAME.equals(token) && "name".equals(parser.currentName())) {

                                token = parser.nextToken();
                                String columnName = parser.getValueAsString();

                                // Check if we already have the same column name
                                if (spreadSheet.getDataStore().getVariable(columnName) == null) {

                                    token = parser.nextToken();
                                    if (JsonToken.FIELD_NAME.equals(token) && "type".equals(parser.currentName())) {
                                        token = parser.nextToken();
                                        ColumnKind variableType = getVarType(parser.getValueAsString());

                                        if (variableType != null) {
                                            token = parser.nextToken();
                                            if (JsonToken.FIELD_NAME.equals(token) && "arguments".equals(parser
                                                    .currentName())) {
                                                token = parser.nextToken();
                                                if (token == null) { break; }
                                                if (!JsonToken.START_OBJECT.equals(token)) { break; }

                                                Variable newColumn = dataStore.createVariable(columnName, true);
                                                added.add(newColumn);
                                                Argument variableArg = newColumn.getRootNode();
                                                variableArg.clearChildArguments();
                                                if (variableType != ColumnKind.MATRIX) {
                                                    variableArg.childArguments.add(new Argument(VALUE_CODE));
                                                }

                                                while (!JsonToken.END_OBJECT.equals(token)) {
                                                    token = parser.nextToken();
                                                    if (token == null) { break; }
                                                    if (JsonToken.START_OBJECT.equals(token) || JsonToken.END_OBJECT
                                                            .equals(token)) {
                                                        break;
                                                    }

                                                    token = parser.nextToken();
                                                    if (variableType == ColumnKind.MATRIX) {
                                                        if (getVarType(parser.getValueAsString()) != null) {
                                                            variableArg.childArguments.add(new Argument(parser
                                                                    .currentName()));
                                                        } else {
                                                            logger.warn("Unknown argument ('" + parser.currentName
                                                                    () + "' line " + parser.currentLocation()
                                                                    .getLineNr() + " ): was expecting " + "argument "
                                                                    + "(NOMINAL, TEXT or MATRIX)" + " field name");
                                                            break;
                                                        }
                                                    }
                                                }
                                                newColumn.setRootNode(variableArg);

                                                token = parser.nextToken();
                                                if (JsonToken.FIELD_NAME.equals(token) && "cells".equals(parser
                                                        .currentName())) {

                                                    token = parser.nextToken();
                                                    if (!JsonToken.START_ARRAY.equals(token)) { break; }


                                                    int cellNumber = 0;
                                                    while (!JsonToken.END_ARRAY.equals(token)) { // for each cell
                                                        token = parser.nextToken();
                                                        if (!JsonToken.START_OBJECT.equals(token)) { break; }
                                                        if (JsonToken.END_ARRAY.equals(token)) { break; }
                                                        if (token == null) { break; }

                                                        Cell newCell = newColumn.createCell();
                                                        cellNumber++;

                                                        token = parser.nextToken();
                                                        if (JsonToken.FIELD_NAME.equals(token) && "id".equals(parser
                                                                .currentName())) {
                                                            token = parser.nextToken(); // we skip the id value
                                                            token = parser.nextToken();
                                                            if (JsonToken.FIELD_NAME.equals(token) && "onset".equals
                                                                    (parser.currentName())) {
                                                                token = parser.nextToken();
                                                                newCell.setOnset(readTime(parser, "onset", columnName,
                                                                        cellNumber));

                                                                token = parser.nextToken();
                                                                if (JsonToken.FIELD_NAME.equals(token) && "offset"
                                                                        .equals(parser.currentName())) {
                                                                    token = parser.nextToken();
                                                                    newCell.setOffset(readTime(parser, "offset",
                                                                            columnName, cellNumber));

                                                                    token = parser.nextToken();
                                                                    if (JsonToken.FIELD_NAME.equals(token) &&
                                                                            "values".equals(parser.currentName())) {
                                                                        token = parser.nextToken();
                                                                        if (!JsonToken.START_ARRAY.equals(token)) {
                                                                            break;
                                                                        }

                                                                        token = parser.nextToken();
                                                                        int k = 0; // keep track of the matrix value
                                                                        // number
                                                                        while (!JsonToken.END_ARRAY.equals(token)) {
                                                                            if (token == null) { break; }

                                                                            newCell.setMatrixValue(k, parser
                                                                                    .getValueAsString());
                                                                            k++;
                                                                            token = parser.nextToken();
                                                                        }
                                                                    } else {
                                                                        dataStore.removeVariable(newColumn);
                                                                        String msg = "Unexpected character ('" +
                                                                                parser.currentName() + "' line " +
                                                                                parser.currentLocation().getLineNr
                                                                                        () + " ): " + "was expecting " +
                                                                                "" + "values field name";
                                                                        logger.warn(msg);
                                                                        Datavyu.getApplication().showWarningDialog(msg);
                                                                        break;
                                                                    }
                                                                } else {
                                                                    dataStore.removeVariable(newColumn);
                                                                    String msg = "Unexpected character ('" + parser
                                                                            .currentName() + "' line " + parser
                                                                            .currentLocation().getLineNr() + " ): " +
                                                                            "" + "was " + "expecting offset field " +
                                                                            "name";
                                                                    logger.warn(msg);
                                                                    Datavyu.getApplication().showWarningDialog(msg);
                                                                    break;
                                                                }
                                                            } else {
                                                                dataStore.removeVariable(newColumn);
                                                                String msg = "Unexpected character ('" + parser
                                                                        .currentName() + "' line " + parser
                                                                        .currentLocation().getLineNr() + " ): was " +
                                                                        "" + "" + "expecting onset field name";
                                                                logger.warn(msg);
                                                                Datavyu.getApplication().showWarningDialog(msg);
                                                                break;
                                                            }
                                                        } else {
                                                            dataStore.removeVariable(newColumn);
                                                            String msg = "Unexpected character ('" + parser
                                                                    .currentName() + "' line " + parser
                                                                    .currentLocation().getLineNr() + " ): was " +
                                                                    "expecting " + "id field name";
                                                            logger.warn(msg);
                                                            Datavyu.getApplication().showWarningDialog(msg);
                                                            break;
                                                        }
                                                        token = parser.nextToken();
                                                    }
                                                } else {
                                                    dataStore.removeVariable(newColumn);
                                                    String msg = "Unexpected character ('" + parser.currentName() +
                                                            "' line " + parser.currentLocation().getLineNr() + " )" +
                                                            ": was expecting cells " + "field name";
                                                    logger.warn(msg);
                                                    Datavyu.getApplication().showWarningDialog(msg);
                                                    break;
                                                    }
                                            } else {
                                                String msg = "Unexpected character ('" + parser.currentName() + "' "
                                                        + "line " + parser.currentLocation().getLineNr() +
                                                        " ):" + " was expecting arguments field name";
                                                logger.warn(msg);
                                                Datavyu.getApplication().showWarningDialog(msg);
                                                break;
                                            }
                                        } else {
                                            String msg = "Unknown argument ('" + parser.currentName() + "' line " +
                                                    parser.currentLocation().getLineNr() + " ): was expecting " +
                                                    "argument (NOMINAL, TEXT or MATRIX) field name";
                                            logger.warn(msg);
                                            Datavyu.getApplication().showWarningDialog(msg);
                                            break;
                                        }
                                    } else {
                                        String msg = "Unexpected character ('" + parser.currentName() + "' line " +
                                                parser.currentLocation().getLineNr() + " ): was expecting type " +
                                                "field name";
                                        logger.warn(msg);
                                        Datavyu.getApplication().showWarningDialog(msg);
                                        break;
                                    }
                                } else {
                                    String msg = "The column " + columnName + " already exists in the current" + " " +
                                            "spreadsheet";
                                    logger.warn(msg);
                                    Datavyu.getApplication().showWarningDialog(msg);
                                    break;
                                }
                            } else {
                                String msg = "Unexpected character ('" + parser.currentName() + "' line " + parser
                                        .currentLocation().getLineNr() + " ): was expecting name field name";
                                logger.warn(msg);
                                Datavyu.getApplication().showWarningDialog(msg);
                                break;
                            }
                            token = parser.nextToken();
                        }
                        parser.close();
                    } else {
                        String msg = "Unexpected character ('" + parser.currentName() + "' line " + parser
                                .currentLocation().getLineNr() + " ): was expecting passes field name";
                        logger.error(msg);
                        Datavyu.getApplication().showWarningDialog(msg);
                        break;
                    }
                } else {
                    String msg ="Unexpected character ('" + parser.currentName() + "' line " + parser
                            .currentLocation().getLineNr() + " ): was expecting '{'";
                    logger.error(msg);
                    Datavyu.getApplication().showWarningDialog(msg);
                    break;
                }
            }
            }
        } else {
            String msg = "The selected file is a not a JSON format";
            logger.error(msg);
            Datavyu.getApplication().showWarningDialog(msg);
        }
    }

    /**
     * @param type The string containing the variable type.
     * @return The type of the variable.
     */
    private ColumnKind getVarType(final String type) {

        if (type.equalsIgnoreCase("text")) {
            return ColumnKind.TEXT;

        } else if (type.equalsIgnoreCase("nominal")) {
            return ColumnKind.NOMINAL;

        } else if (type.equalsIgnoreCase("predicate")) {
            // TODO - support predicate types.
            return null;

        } else if (type.equalsIgnoreCase("matrix")) {
            return ColumnKind.MATRIX;

        } else if (type.equalsIgnoreCase("integer")) {
            // TODO - support integer types.
            return null;

        } else if (type.equalsIgnoreCase("float")) {
            // TODO - support float types.
            return null;
        }

        // Error - Unknown type.
        return null;
    }
}

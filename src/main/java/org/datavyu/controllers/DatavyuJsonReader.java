/*
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.datavyu.util.Timestamp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reads Datavyu's JSON export into passes, refusing any part Watavyu cannot read and saying where. The fields of the
 * file, its passes and their cells come in the order Datavyu writes them.
 */
final class DatavyuJsonReader {

    /**
     * The one code of an annotation column converted from a Datavyu text or nominal column, named as Datavyu named
     * that column's value.
     */
    static final String VALUE_CODE = "var";

    /** The parser of the file being read */
    private final JsonParser parser;

    private DatavyuJsonReader(final JsonParser parser) {
        this.parser = parser;
    }

    /**
     * Datavyu's column types, which are also the types of a matrix column's codes. Watavyu has only annotation columns
     * (Datavyu's matrix columns): a text or nominal column opens as an annotation column with one code,
     * {@value #VALUE_CODE}, holding its values.
     */
    private enum ColumnKind {
        TEXT,
        NOMINAL,
        MATRIX
    }

    /** A pass read from the file, to be added as an annotation column. */
    record Pass(String name, List<String> codes, List<PassCell> cells) {
    }

    /** A cell of a pass, with a value for each of its column's codes. */
    record PassCell(long onset, long offset, List<String> values) {
    }

    /** A part of the file that Watavyu cannot take, described for the user with its line. */
    static final class UnimportableException extends Exception {
        private static final long serialVersionUID = 1L;

        UnimportableException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    private UnimportableException fault(final String problem) {
        return new UnimportableException(problem + " (line " + parser.currentLocation().getLineNr()
            + "). Nothing was imported.", null);
    }

    /** Moves to the next token, which a file that ends early does not have. */
    private JsonToken next() throws IOException, UnimportableException {
        JsonToken token = parser.nextToken();
        if (token == null) {
            throw fault("the file ends early");
        }
        return token;
    }

    /** Moves to the next token, refusing the file unless it is the one expected there. */
    private void expect(final JsonToken expected, final String problem)
            throws IOException, UnimportableException {
        if (next() != expected) {
            throw fault(problem);
        }
    }

    /** Moves to the next field, refusing the file unless it has the name expected, and then to its value. */
    private void expectField(final String field, final String owner)
            throws IOException, UnimportableException {
        if (next() != JsonToken.FIELD_NAME || !field.equals(parser.currentName())) {
            throw fault(owner + " should have \"" + field + "\" next");
        }
        next();
    }

    private static String column(final String name) {
        return "column \"" + name + "\"";
    }

    /**
     * Reads the passes of a file.
     *
     * @param parser a parser at the start of the file
     * @return the passes, in the file's order
     * @throws UnimportableException if any part of the file cannot be read
     * @throws IOException           if the file cannot be read or is not JSON
     */
    static List<Pass> read(final JsonParser parser) throws IOException, UnimportableException {
        return new DatavyuJsonReader(parser).readFile();
    }

    private List<Pass> readFile() throws IOException, UnimportableException {
        expect(JsonToken.START_OBJECT, "the file is not a JSON object");
        expectField("passes", "the file");
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw fault("the file's passes are not a list");
        }
        List<Pass> passes = new ArrayList<>();
        for (JsonToken token = next(); token != JsonToken.END_ARRAY; token = next()) {
            int number = passes.size() + 1;
            if (token != JsonToken.START_OBJECT) {
                throw fault("pass " + number + " is not a JSON object");
            }
            passes.add(readPass(number));
        }
        expect(JsonToken.END_OBJECT, "the file should end after its passes");
        return passes;
    }

    private Pass readPass(final int number) throws IOException, UnimportableException {
        expectField("name", "pass " + number);
        String name = parser.currentToken() == JsonToken.VALUE_STRING ? parser.getText() : "";
        if (name.isEmpty()) {
            throw fault("pass " + number + " has no name");
        }

        expectField("type", column(name));
        ColumnKind kind = kindAt();
        if (kind == null) {
            throw fault(column(name) + " has a type that is not TEXT, NOMINAL or MATRIX");
        }

        expectField("arguments", column(name));
        List<String> codes = readCodes(kind, name);

        expectField("cells", column(name));
        List<PassCell> cells = readCells(codes.size(), name);
        expect(JsonToken.END_OBJECT, column(name) + " should end after its cells");
        return new Pass(name, codes, cells);
    }

    private List<PassCell> readCells(final int codes, final String column)
            throws IOException, UnimportableException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw fault(column(column) + " has cells that are not a list");
        }
        List<PassCell> cells = new ArrayList<>();
        for (JsonToken token = next(); token != JsonToken.END_ARRAY; token = next()) {
            int cellNumber = cells.size() + 1;
            if (token != JsonToken.START_OBJECT) {
                throw fault("cell " + cellNumber + " of " + column(column) + " is not a JSON object");
            }
            cells.add(readCell(codes, column, cellNumber));
        }
        return cells;
    }

    /** Returns the column kind or code type the parser is on, or null if it is not one Watavyu reads. */
    private ColumnKind kindAt() throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return null;
        }
        return switch (parser.getText().toUpperCase(Locale.ROOT)) {
            case "TEXT" -> ColumnKind.TEXT;
            case "NOMINAL" -> ColumnKind.NOMINAL;
            case "MATRIX" -> ColumnKind.MATRIX;
            default -> null;
        };
    }

    /**
     * Reads a pass's arguments: a matrix column's codes and their types, or what a text or nominal column has in
     * their place, which is not read.
     */
    private List<String> readCodes(final ColumnKind kind, final String column)
            throws IOException, UnimportableException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw fault(column(column) + " has arguments that are not a JSON object");
        }
        List<String> codes = new ArrayList<>();
        if (kind != ColumnKind.MATRIX) {
            codes.add(VALUE_CODE);
            parser.skipChildren();
            return codes;
        }
        while (next() != JsonToken.END_OBJECT) {
            String code = codeName(codes, column);
            next();
            if (kindAt() == null) {
                throw fault(column(column) + " has the code \"" + code
                    + "\" of a type that is not TEXT, NOMINAL or MATRIX");
            }
            codes.add(code);
        }
        return codes;
    }

    /** Returns the name of the code the parser is on, which a project file requires be neither empty nor another's. */
    private String codeName(final List<String> codes, final String column) throws IOException, UnimportableException {
        String code = parser.currentName();
        if (code.isEmpty()) {
            throw fault(column(column) + " has a code with no name");
        }
        if (codes.contains(code)) {
            throw fault(column(column) + " has two codes named \"" + code + "\"");
        }
        return code;
    }

    /**
     * Reads a cell, whose id is not kept.
     *
     * @param codes      how many codes the cell's column has
     * @param column     the cell's column
     * @param cellNumber the cell's place in its column, from 1
     */
    private PassCell readCell(final int codes, final String column, final int cellNumber)
            throws IOException, UnimportableException {
        String owner = "cell " + cellNumber + " of " + column(column);
        expectField("id", owner);
        parser.skipChildren();
        expectField("onset", owner);
        long onset = readTime("onset", owner);
        expectField("offset", owner);
        long offset = readTime("offset", owner);

        expectField("values", owner);
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw fault(owner + " has values that are not a list");
        }
        List<String> values = new ArrayList<>(Collections.nCopies(codes, ""));
        int code = 0;
        while (next() != JsonToken.END_ARRAY) {
            // A number or true or false is read as its text.
            String text = parser.getValueAsString();
            if (text == null) {
                throw fault(owner + " has a value that is not text or a number");
            }
            if (code >= codes) {
                throw fault(owner + " has more values than its column has codes");
            }
            values.set(code, text);
            code++;
        }
        expect(JsonToken.END_OBJECT, owner + " should end after its values");
        return new PassCell(onset, offset, values);
    }

    /**
     * Reads a cell's onset or offset.
     *
     * @param field  {@code onset} or {@code offset}
     * @param owner  the cell, as the user is told of it
     * @throws UnimportableException if the value is not a time, or is negative
     */
    private long readTime(final String field, final String owner) throws IOException, UnimportableException {
        String text = parser.getValueAsString();
        try {
            return Timestamp.parse(text);
        } catch (IllegalArgumentException e) {
            throw new UnimportableException(owner + " has the " + field + " "
                + (text == null ? "null" : "\"" + text + "\"") + " (line " + parser.currentLocation().getLineNr()
                + "), which is not a time. Times are HH:MM:SS:mmm and never negative. Nothing was imported.", e);
        }
    }
}

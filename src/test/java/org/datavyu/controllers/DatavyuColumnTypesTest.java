package org.datavyu.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.Variable;
import org.datavyu.projectfile.ProjectDocument.AnnotationCell;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.datavyu.projectfile.SpreadsheetColumns;
import org.datavyu.views.discrete.SpreadSheetPanel;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Datavyu's text and nominal columns open as annotation columns with one code, {@code var}, when Datavyu's
 * JSON is imported; matrix columns open with their codes.
 */
public class DatavyuColumnTypesTest {

    private static List<AnnotationColumn> byName(final DataStore dataStore) {
        return SpreadsheetColumns.of(dataStore).stream().sorted(Comparator.comparing(AnnotationColumn::name)).toList();
    }

    @Test
    public void jsonPassesImportAsAnnotationColumns() throws Exception {
        String json = """
            {"passes": [
              {"name": "notes", "type": "TEXT", "arguments": {},
               "cells": [{"id": 1, "onset": "00:00:01:000", "offset": "00:00:02:000", "values": ["a, (b)"]}]},
              {"name": "kind", "type": "NOMINAL", "arguments": {"var": "NOMINAL"},
               "cells": [{"id": 1, "onset": "00:00:03:000", "offset": "00:00:03:000", "values": ["happy"]}]},
              {"name": "mother", "type": "MATRIX", "arguments": {"mood": "NOMINAL", "gaze": "TEXT"},
               "cells": [{"id": 1, "onset": "00:00:04:000", "offset": "00:00:05:000", "values": ["calm", "away"]}]}
            ]}
            """;

        DataStore dataStore = importJson(json);

        assertEquals(byName(dataStore), List.of(
            new AnnotationColumn("kind", List.of("var"), false, List.of(
                new AnnotationCell(3_000, 3_000, List.of("happy")))),
            new AnnotationColumn("mother", List.of("mood", "gaze"), false, List.of(
                new AnnotationCell(4_000, 5_000, List.of("calm", "away")))),
            new AnnotationColumn("notes", List.of("var"), false, List.of(
                new AnnotationCell(1_000, 2_000, List.of("a, (b)"))))));
    }

    /** Exported JSON names every column a matrix of nominal codes, as Datavyu reads it, and imports unchanged. */
    @Test
    public void exportedJsonImportsUnchanged() throws Exception {
        DataStore original = DataStoreFactory.newDataStore();
        Variable mother = original.createVariable("mother");
        mother.addArgument();
        var cell = mother.createCell();
        cell.setOnset(4_000);
        cell.setOffset(5_000);
        cell.setMatrixValue(0, "calm, (mostly)");
        cell.setMatrixValue(1, "away");
        Path file = Files.createTempFile("export", ".json");
        try {
            new ExportDatabaseFileController().exportAsJSON(file.toString(), original);

            String exported = Files.readString(file);
            assertEquals(exported.contains("\"TEXT\""), false, exported);
            assertEquals(exported.contains("\"type\" : \"MATRIX\""), true, exported);

            assertEquals(byName(importJson(exported)), byName(original));
        } finally {
            Files.delete(file);
        }
    }

    private static DataStore importJson(final String json) throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        SpreadSheetPanel spreadsheet = mock(SpreadSheetPanel.class);
        when(spreadsheet.getDataStore()).thenReturn(dataStore);
        Path file = Files.createTempFile("import", ".json");
        try {
            Files.writeString(file, json);
            new OpenDataStoreFileController().importJSONToSpreadsheet(new File(file.toString()), spreadsheet);
        } finally {
            Files.delete(file);
        }
        return dataStore;
    }
}

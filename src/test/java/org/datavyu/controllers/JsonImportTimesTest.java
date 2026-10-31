package org.datavyu.controllers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.DataStoreFactory;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.datavyu.views.discrete.SpreadSheetPanel;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Importing Datavyu's JSON refuses a file with a time Watavyu cannot read, such as a negative one Datavyu could
 * write: nothing is added, and the message says which time is wrong.
 */
public class JsonImportTimesTest {

    private static final String READABLE_PASS = """
        {"name": "kind", "type": "NOMINAL", "arguments": {"var": "NOMINAL"},
         "cells": [{"id": 1, "onset": "00:00:03:000", "offset": "00:00:03:000", "values": ["happy"]}]}""";

    private static String passes(final String onset, final String offset) {
        return "{\"passes\": [\n" + READABLE_PASS + ",\n"
            + "{\"name\": \"mother\", \"type\": \"MATRIX\", \"arguments\": {\"mood\": \"NOMINAL\"},\n"
            + " \"cells\": [{\"id\": 1, \"onset\": \"00:00:01:000\", \"offset\": \"00:00:02:000\", \"values\": [\"calm\"]},\n"
            + "  {\"id\": 2, \"onset\": " + onset + ", \"offset\": " + offset + ", \"values\": [\"cross\"]}]}\n"
            + "]}";
    }

    @Test
    public void aNegativeOnsetImportsNothingAndSaysWhichTimeIsWrong() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(passes("\"-00:00:01:000\"", "\"00:00:04:000\""), dataStore));

        assertTrue(dataStore.getAllVariables().isEmpty(), "the pass read before the bad time is removed too");
        assertTrue(e.getMessage().matches("Cannot import import\\d+\\.json: cell 2 of column \"mother\" has the onset "
            + "\"-00:00:01:000\" \\(line 6\\), which is not a time\\. Times are HH:MM:SS:mmm and never negative\\. "
            + "Nothing was imported\\."), e.getMessage());
    }

    @Test
    public void anUnreadableOffsetImportsNothing() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(passes("\"00:00:04:000\"", "\"soon\""), dataStore));

        assertTrue(dataStore.getAllVariables().isEmpty());
        assertTrue(e.getMessage().contains("cell 2 of column \"mother\" has the offset \"soon\""), e.getMessage());
    }

    @Test
    public void aMissingTimeIsUnreadable() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(passes("null", "\"00:00:04:000\""), dataStore));

        assertTrue(dataStore.getAllVariables().isEmpty());
        assertTrue(e.getMessage().contains("has the onset null"), e.getMessage());
    }

    @Test
    public void aRefusedFileLeavesTheSpreadsheetAsItWas() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Variable notes = dataStore.createVariable("notes");
        notes.createCell().setOnset(1_000);
        dataStore.markAsUnchanged();

        expectThrows(UserWarningException.class,
            () -> importJson(passes("\"-00:00:01:000\"", "\"00:00:04:000\""), dataStore));

        assertEquals(dataStore.getAllVariables(), List.of(notes));
        assertEquals(notes.getCells().size(), 1);
        assertFalse(dataStore.isChanged(), "an import that added nothing leaves no unsaved changes");
    }

    @Test
    public void aSpreadsheetWithUnsavedChangesKeepsThem() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createVariable("notes");
        dataStore.markAsChanged();

        expectThrows(UserWarningException.class,
            () -> importJson(passes("\"-00:00:01:000\"", "\"00:00:04:000\""), dataStore));

        assertTrue(dataStore.isChanged());
    }

    @Test
    public void millisecondsWithoutColonsAreATime() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        importJson(passes("\"4000\"", "\"00:00:05:000\""), dataStore);

        assertEquals(dataStore.getVariable("mother").getCells().get(1).getOnset(), 4_000);
    }

    private static void importJson(final String json, final DataStore dataStore) throws Exception {
        SpreadSheetPanel spreadsheet = mock(SpreadSheetPanel.class);
        when(spreadsheet.getDataStore()).thenReturn(dataStore);
        Path file = Files.createTempFile("import", ".json");
        try {
            Files.writeString(file, json);
            new OpenDataStoreFileController().importJSONToSpreadsheet(new File(file.toString()), spreadsheet);
        } finally {
            Files.delete(file);
        }
    }
}

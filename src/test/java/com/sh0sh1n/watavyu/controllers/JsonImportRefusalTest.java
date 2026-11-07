package com.sh0sh1n.watavyu.controllers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import com.sh0sh1n.watavyu.models.db.DataStore;
import com.sh0sh1n.watavyu.models.db.DataStoreFactory;
import com.sh0sh1n.watavyu.models.db.UserWarningException;
import com.sh0sh1n.watavyu.models.db.Column;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Importing Datavyu's JSON refuses a file with a time Watavyu cannot read, such as a negative one Datavyu could
 * write, a value that is not text or has no code, or a code a project file could not save: nothing is added, and the
 * message says which cell or code is wrong.
 */
public class JsonImportRefusalTest {

    private static final String READABLE_PASS = """
        {"name": "kind", "type": "NOMINAL", "arguments": {"var": "NOMINAL"},
         "cells": [{"id": 1, "onset": "00:00:03:000", "offset": "00:00:03:000", "values": ["happy"]}]}""";

    private static String passes(final String onset, final String offset) {
        return passes(onset, offset, "\"cross\"");
    }

    private static String passes(final String onset, final String offset, final String values) {
        return "{\"passes\": [\n" + READABLE_PASS + ",\n"
            + "{\"name\": \"mother\", \"type\": \"MATRIX\", \"arguments\": {\"mood\": \"NOMINAL\"},\n"
            + " \"cells\": [{\"id\": 1, \"onset\": \"00:00:01:000\", \"offset\": \"00:00:02:000\", \"values\": [\"calm\"]},\n"
            + "  {\"id\": 2, \"onset\": " + onset + ", \"offset\": " + offset + ", \"values\": [" + values + "]}]}\n"
            + "]}";
    }

    @Test
    public void aNegativeOnsetImportsNothingAndSaysWhichTimeIsWrong() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(passes("\"-00:00:01:000\"", "\"00:00:04:000\""), dataStore));

        assertTrue(dataStore.getAllColumns().isEmpty(), "the pass read before the bad time is removed too");
        assertTrue(e.getMessage().matches("Cannot import import\\d+\\.json: cell 2 of column \"mother\" has the onset "
            + "\"-00:00:01:000\" \\(line 6\\), which is not a time\\. Times are HH:MM:SS:mmm and never negative\\. "
            + "Nothing was imported\\."), e.getMessage());
    }

    @Test
    public void anUnreadableOffsetImportsNothing() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(passes("\"00:00:04:000\"", "\"soon\""), dataStore));

        assertTrue(dataStore.getAllColumns().isEmpty());
        assertTrue(e.getMessage().contains("cell 2 of column \"mother\" has the offset \"soon\""), e.getMessage());
    }

    @Test
    public void aMissingTimeIsUnreadable() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(passes("null", "\"00:00:04:000\""), dataStore));

        assertTrue(dataStore.getAllColumns().isEmpty());
        assertTrue(e.getMessage().contains("has the onset null"), e.getMessage());
    }

    @Test
    public void aValueThatIsNotTextImportsNothing() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(passes("\"00:00:03:000\"", "\"00:00:04:000\"", "null"), dataStore));

        assertTrue(dataStore.getAllColumns().isEmpty());
        assertTrue(e.getMessage().endsWith("cell 2 of column \"mother\" has a value that is not text or a number (line 6). "
            + "Nothing was imported."), e.getMessage());
    }

    @Test
    public void aNumberIsReadAsItsText() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        importJson(passes("\"00:00:03:000\"", "\"00:00:04:000\"", "3"), dataStore);

        assertEquals(dataStore.getColumn("mother").getCells().get(1).getValues(), List.of("3"));
    }

    @Test
    public void moreValuesThanCodesImportsNothing() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(passes("\"00:00:03:000\"", "\"00:00:04:000\"", "\"cross\", \"loud\""), dataStore));

        assertTrue(dataStore.getAllColumns().isEmpty());
        assertTrue(e.getMessage().contains("cell 2 of column \"mother\" has more values than its column has codes"),
            e.getMessage());
    }

    /** A project file needs each code named, and named once, so the import refuses what it could not save. */
    @Test
    public void aCodeWithNoNameImportsNothing() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(withCodes("{\"mood\": \"NOMINAL\", \"\": \"NOMINAL\"}"), dataStore));

        assertTrue(dataStore.getAllColumns().isEmpty());
        assertTrue(e.getMessage().endsWith("column \"mother\" has a code with no name (line 4). Nothing was imported."),
            e.getMessage());
    }

    @Test
    public void twoCodesOfOneNameImportNothing() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(withCodes("{\"mood\": \"NOMINAL\", \"mood\": \"NOMINAL\"}"), dataStore));

        assertTrue(dataStore.getAllColumns().isEmpty(), "the pass read before is removed too");
        assertTrue(e.getMessage().contains("column \"mother\" has two codes named \"mood\""), e.getMessage());
    }

    private static String withCodes(final String arguments) {
        return "{\"passes\": [\n" + READABLE_PASS + ",\n"
            + "{\"name\": \"mother\", \"type\": \"MATRIX\", \"arguments\": " + arguments + ", \"cells\": []}\n"
            + "]}";
    }

    @Test
    public void aRefusedFileLeavesTheSpreadsheetAsItWas() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column notes = dataStore.createColumn("notes");
        notes.createCell().setOnset(1_000);
        dataStore.markAsUnchanged();

        expectThrows(UserWarningException.class,
            () -> importJson(passes("\"-00:00:01:000\"", "\"00:00:04:000\""), dataStore));

        assertEquals(dataStore.getAllColumns(), List.of(notes));
        assertEquals(notes.getCells().size(), 1);
        assertFalse(dataStore.isChanged(), "an import that added nothing leaves no unsaved changes");
    }

    @Test
    public void aSpreadsheetWithUnsavedChangesKeepsThem() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        dataStore.createColumn("notes");
        dataStore.markAsChanged();

        expectThrows(UserWarningException.class,
            () -> importJson(passes("\"-00:00:01:000\"", "\"00:00:04:000\""), dataStore));

        assertTrue(dataStore.isChanged());
    }

    @Test
    public void millisecondsWithoutColonsAreATime() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        importJson(passes("\"4000\"", "\"00:00:05:000\""), dataStore);

        assertEquals(dataStore.getColumn("mother").getCells().get(1).getOnset(), 4_000);
    }

    /** A file whose second pass, after one that reads, is given. */
    private static String withPass(final String pass) {
        return "{\"passes\": [\n" + READABLE_PASS + ",\n" + pass + "\n]}";
    }

    private static String mother(final String type, final String arguments, final String cells) {
        return "{\"name\": \"mother\", \"type\": " + type + ", \"arguments\": " + arguments + ", \"cells\": " + cells
            + "}";
    }

    private static final String NOT_A_TYPE = "column \"mother\" has a type that is not TEXT, NOMINAL or MATRIX";
    private static final String NOT_A_CODE_TYPE =
        "column \"mother\" has the code \"mood\" of a type that is not TEXT, NOMINAL or MATRIX";

    /** A pass of the name given, as JSON. */
    private static String named(final String name) {
        return "{\"name\": " + name + ", \"type\": \"MATRIX\", \"arguments\": {}, \"cells\": []}";
    }

    private static final String CELL =
        "{\"id\": 1, \"onset\": \"00:00:01:000\", \"offset\": \"00:00:02:000\", \"values\": [\"calm\"]}";

    @DataProvider
    public Object[][] unreadableFiles() {
        return new Object[][] {
            {withPass(named("null")), "pass 2 has no name"},
            {withPass(named("7")), "pass 2 has no name"},
            {withPass(named("\"\"")), "pass 2 has no name"},
            {withPass(named("{}")), "pass 2 has no name"},
            {withPass(mother("null", "{}", "[]")), NOT_A_TYPE},
            {withPass(mother("{}", "{}", "[]")), NOT_A_TYPE},
            {withPass(mother("[]", "{}", "[]")), NOT_A_TYPE},
            {withPass(mother("\"PREDICATE\"", "{}", "[]")), NOT_A_TYPE},
            {withPass(mother("\"MATRIX\"", "{\"mood\": null}", "[]")), NOT_A_CODE_TYPE},
            {withPass(mother("\"MATRIX\"", "{\"mood\": {}}", "[]")), NOT_A_CODE_TYPE},
            {withPass(mother("\"MATRIX\"", "{\"mood\": []}", "[]")), NOT_A_CODE_TYPE},
            {withPass(mother("\"MATRIX\"", "{\"mood\": \"INTEGER\"}", "[]")), NOT_A_CODE_TYPE},
            {withPass(mother("\"NOMINAL\"", "[]", "[]")), "column \"mother\" has arguments that are not a JSON object"},
            {withPass(mother("\"MATRIX\"", "{\"mood\": \"NOMINAL\"}", "{}")),
                "column \"mother\" has cells that are not a list"},
            {withPass(mother("\"MATRIX\"", "{\"mood\": \"NOMINAL\"}", "[3]")),
                "cell 1 of column \"mother\" is not a JSON object"},
            {withPass(mother("\"MATRIX\"", "{\"mood\": \"NOMINAL\"}", "[" + CELL.replace("\"id\": 1, ", "") + "]")),
                "cell 1 of column \"mother\" should have \"id\" next"},
            {withPass(mother("\"MATRIX\"", "{\"mood\": \"NOMINAL\"}",
                "[" + CELL.replace("[\"calm\"]", "\"calm\"") + "]")),
                "cell 1 of column \"mother\" has values that are not a list"},
            {withPass(mother("\"MATRIX\"", "{\"mood\": \"NOMINAL\"}", "[" + CELL.replace("]}", "], \"x\": 1}") + "]")),
                "cell 1 of column \"mother\" should end after its values"},
            {withPass(mother("\"MATRIX\"", "{\"mood\": \"NOMINAL\"}", "[], \"colour\": \"red\"")),
                "column \"mother\" should end after its cells"},
            {withPass("{\"type\": \"MATRIX\", \"name\": \"mother\", \"arguments\": {}, \"cells\": []}"),
                "pass 2 should have \"name\" next"},
            {withPass("3"), "pass 2 is not a JSON object"},
            {"", "the file ends early"},
            {"[]", "the file is not a JSON object"},
            {"{\"columns\": []}", "the file should have \"passes\" next"},
            {"{\"passes\": {}}", "the file's passes are not a list"},
            {"{\"passes\": [], \"name\": \"study\"}", "the file should end after its passes"},
        };
    }

    /** A file with any part Watavyu cannot read adds nothing, and the message says what and where. */
    @Test(dataProvider = "unreadableFiles")
    public void anUnreadablePartImportsNothingAndSaysWhere(final String json, final String problem) throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        UserWarningException e = expectThrows(UserWarningException.class, () -> importJson(json, dataStore));

        assertTrue(dataStore.getAllColumns().isEmpty(), "the pass read before is not added");
        assertTrue(e.getMessage().matches("Cannot import import\\d+\\.json: " + Pattern.quote(problem)
            + " \\(line \\d\\)\\. Nothing was imported\\."), e.getMessage());
    }

    @Test
    public void theLineGivenIsTheOneWithTheFault() throws Exception {
        UserWarningException e = expectThrows(UserWarningException.class,
            () -> importJson(withPass(mother("\n\"PREDICATE\"", "{}", "[]")), DataStoreFactory.newDataStore()));

        assertTrue(e.getMessage().endsWith("(line 5). Nothing was imported."), e.getMessage());
    }

    @Test
    public void aFileThatIsNotJsonImportsNothing() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        expectThrows(IOException.class, () -> importJson(withPass("{\"name\": \"mother\""), dataStore));
        expectThrows(IOException.class, () -> importJson(withPass("{\"name\": mother}"), dataStore));

        assertTrue(dataStore.getAllColumns().isEmpty());
    }

    @Test
    public void fewerValuesThanCodesLeaveTheRestEmpty() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        importJson(withPass(mother("\"MATRIX\"", "{\"mood\": \"NOMINAL\", \"gaze\": \"TEXT\"}", "[" + CELL + "]")),
            dataStore);

        assertEquals(dataStore.getColumn("mother").getCells().get(0).getValues(), List.of("calm", ""));
    }

    /** Datavyu reads its types in any case, and a cell's id, which Watavyu does not keep, in any form. */
    @Test
    public void typesAreReadInAnyCaseAndIdsNotAtAll() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();

        importJson(withPass(mother("\"matrix\"", "{\"mood\": \"Nominal\"}",
            "[" + CELL.replace("\"id\": 1", "\"id\": {\"n\": [1]}") + "]")), dataStore);

        assertEquals(dataStore.getColumn("mother").getCodes(), List.of("mood"));
        assertEquals(dataStore.getColumn("mother").getCells().get(0).getValues(), List.of("calm"));
    }

    /** A pass whose name the spreadsheet has, as it was or from earlier in the file, is left out, and the rest read. */
    @Test
    public void aPassWhoseNameTheSpreadsheetHasIsLeftOut() throws Exception {
        DataStore dataStore = DataStoreFactory.newDataStore();
        Column kind = dataStore.createColumn("kind");
        List<String> warnings = new ArrayList<>();

        importJson("{\"passes\": [\n" + READABLE_PASS + ",\n" + mother("\"MATRIX\"", "{}", "[]") + ",\n"
            + mother("\"NOMINAL\"", "{}", "[]") + "\n]}", dataStore, warnings::add);

        assertEquals(dataStore.getAllColumns().stream().map(Column::getName).toList(), List.of("kind", "mother"));
        assertTrue(kind.getCells().isEmpty(), "the spreadsheet's own column is left as it was");
        assertEquals(dataStore.getColumn("mother").getCodes(), List.of());
        assertEquals(warnings, List.of("Left out the columns the spreadsheet already has: \"kind\", \"mother\"."));
    }

    @Test
    public void aFileThatImportsWholeWarnsOfNothing() throws Exception {
        List<String> warnings = new ArrayList<>();

        importJson(passes("\"00:00:03:000\"", "\"00:00:04:000\""), DataStoreFactory.newDataStore(), warnings::add);

        assertEquals(warnings, List.of());
    }

    private static void importJson(final String json, final DataStore dataStore) throws Exception {
        importJson(json, dataStore, message -> { });
    }

    private static void importJson(final String json, final DataStore dataStore, final Consumer<String> warnings)
            throws Exception {
        Path file = Files.createTempFile("import", ".json");
        try {
            Files.writeString(file, json);
            new OpenDataStoreFileController(warnings).importJSONToSpreadsheet(file.toFile(), dataStore);
        } finally {
            Files.delete(file);
        }
    }
}

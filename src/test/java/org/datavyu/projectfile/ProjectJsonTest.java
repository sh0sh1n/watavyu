package org.datavyu.projectfile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.datavyu.projectfile.ProjectDocument.Clip;
import org.datavyu.projectfile.ProjectDocument.AnnotationCell;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.datavyu.projectfile.ProjectDocument.Source;
import org.datavyu.projectfile.ProjectDocument.MediaColumn;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;

public class ProjectJsonTest {

    private static final Path EXAMPLE =
        Paths.get(System.getProperty("testPath"), "projectfile", "example.watavyu.json");

    /** A small valid project; each invalid case below changes one part of it. */
    private static final String BASE = """
        {
          "format": "watavyu",
          "version": 1,
          "columns": [
            {
              "name": "A",
              "type": "annotation",
              "codes": ["x"],
              "settings": { "hidden": false },
              "cells": [
                { "onset": "00:00:01:000", "offset": "00:00:02:000", "values": ["v"] }
              ]
            },
            {
              "name": "V",
              "type": "media",
              "settings": { "hidden": false, "locked": false },
              "cells": [
                {
                  "onset": "00:00:00:000",
                  "offset": "00:00:10:000",
                  "settings": { "volume": 1 },
                  "source": {
                    "file": "a.mp4", "duration": "00:00:10:000", "frameRate": 30, "width": 640, "height": 480
                  }
                }
              ]
            }
          ]
        }
        """;

    private static Schema schema() throws IOException {
        try (InputStream in = ProjectJson.class.getResourceAsStream("watavyu-project.schema.json")) {
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(in);
        }
    }

    private static List<Error> schemaErrors(final String json) throws IOException {
        return schema().validate(json, InputFormat.JSON);
    }

    private static ProjectDocument read(final String json) throws ProjectFileException, IOException {
        return ProjectJson.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String change(final String target, final String replacement) {
        assertEquals(BASE.split(java.util.regex.Pattern.quote(target), -1).length, 2, "not unique in BASE: " + target);
        return BASE.replace(target, replacement);
    }

    @Test
    public void exampleIsRewrittenByteForByte() throws Exception {
        String text = Files.readString(EXAMPLE);
        assertEquals(ProjectJson.toJson(ProjectJson.read(EXAMPLE)), text);
    }

    @Test
    public void exampleMatchesTheSchema() throws Exception {
        assertEquals(schemaErrors(Files.readString(EXAMPLE)), List.of());
    }

    @Test
    public void baseIsValid() throws Exception {
        assertEquals(schemaErrors(BASE), List.of());
        read(BASE);
    }

    @Test
    public void exampleReadsAsTheProjectItDescribes() throws Exception {
        ProjectDocument document = ProjectJson.read(EXAMPLE);
        assertEquals(document.columns().size(), 4);

        MediaColumn camera = (MediaColumn) document.columns().get(0);
        assertEquals(camera.name(), "Camera_1");
        assertTrue(camera.locked());
        Clip clip = camera.clips().get(0);
        assertEquals(clip.onset(), -2_500L);
        assertEquals(clip.offset(), 866_120L);
        assertEquals(clip.volume(), 0.8);
        assertEquals(clip.source(), new Source("media/session-04-cam1.mp4", 868_620L, 29.97, 1920, 1080));

        AnnotationColumn mother = (AnnotationColumn) document.columns().get(1);
        assertEquals(mother.codes(), List.of("look", "affect"));
        assertEquals(mother.cells().get(1), new AnnotationCell(130_000L, 130_000L, List.of("", "neutral")));

        AnnotationColumn notes = (AnnotationColumn) document.columns().get(2);
        assertTrue(notes.hidden());
        assertEquals(notes.cells().get(0).values(), List.of("Sibling enters \"frame\"\nthen leaves; 日本"));
    }

    @Test
    public void aDocumentSurvivesWritingAndReading() throws Exception {
        ProjectDocument document = new ProjectDocument(List.of(
            new AnnotationColumn("Tricky", List.of("a", "b\\c"), false, List.of(
                new AnnotationCell(0, 5, List.of("tab\there", "\u0001control")),
                new AnnotationCell(360_000_000L, 360_000_001L, List.of("", "")))),
            new MediaColumn("Two clips", true, false, List.of(
                new Clip(-1, 0.333333, new Source("/Volumes/Share/a b.mp4", 1, 23.976, 1, 1)),
                new Clip(10, 0, new Source("x.mp4", 0, 0.5, 7680, 4320)))),
            new MediaColumn("No clips", false, false, List.of())));
        String json = ProjectJson.toJson(document);
        assertEquals(read(json), document);
        assertEquals(schemaErrors(json), List.of());
    }

    @Test
    public void anOffsetBeforeTheOnsetIsKept() throws Exception {
        String json = change("\"offset\": \"00:00:02:000\"", "\"offset\": \"00:00:00:500\"");

        AnnotationCell cell = ((AnnotationColumn) read(json).columns().get(0)).cells().get(0);

        assertEquals(cell, new AnnotationCell(1_000, 500, List.of("v")));
        assertEquals(schemaErrors(json), List.of());
        assertTrue(ProjectJson.toJson(read(json)).contains("\"onset\": \"00:00:01:000\", \"offset\": \"00:00:00:500\""));
    }

    @Test
    public void aColumnWithNoCodesHoldsTimingOnly() throws Exception {
        ProjectDocument document = new ProjectDocument(List.of(
            new AnnotationColumn("Timing", List.of(), false, List.of(new AnnotationCell(1, 2, List.of())))));
        String json = ProjectJson.toJson(document);
        assertTrue(json.contains("\"codes\": [],"), json);
        assertTrue(json.contains("\"values\": [] }"), json);
        assertEquals(read(json), document);
        assertEquals(schemaErrors(json), List.of());
    }

    @Test
    public void anEmptyProjectHasNoColumns() throws Exception {
        String json = ProjectJson.toJson(new ProjectDocument(List.of()));
        assertEquals(json, "{\n  \"format\": \"watavyu\",\n  \"version\": 1,\n  \"columns\": []\n}\n");
        assertEquals(read(json), new ProjectDocument(List.of()));
        assertEquals(schemaErrors(json), List.of());
    }

    @Test
    public void cellsAndClipsAreWrittenInTimeOrder() throws Exception {
        Source source = new Source("a.mp4", 1_000, 30, 2, 2);
        ProjectDocument document = new ProjectDocument(List.of(
            new AnnotationColumn("A", List.of("x"), false, List.of(
                new AnnotationCell(5, 9, List.of("third")),
                new AnnotationCell(5, 6, List.of("second")),
                new AnnotationCell(1, 1, List.of("first")))),
            new MediaColumn("V", false, false, List.of(new Clip(50, 1, source), new Clip(-50, 1, source)))));
        ProjectDocument reread = read(ProjectJson.toJson(document));
        assertEquals(((AnnotationColumn) reread.columns().get(0)).cells().stream()
            .map(cell -> cell.values().get(0)).toList(), List.of("first", "second", "third"));
        assertEquals(((MediaColumn) reread.columns().get(1)).clips().stream().map(Clip::onset).toList(),
            List.of(-50L, 50L));
    }

    /**
     * What a clip's media file says is known only once it has opened, and an audio file has no size or frame rate:
     * a detail never given is left out, and a clip without a known duration has no offset.
     */
    @Test
    public void aClipsUnknownDetailsAreLeftOut() throws Exception {
        ProjectDocument document = new ProjectDocument(List.of(new MediaColumn("V", false, false, List.of(
            new Clip(2_000, 1, new Source("never-opened.mp4", 0, 0, 0, 0)),
            new Clip(9_000, 1, new Source("voice.m4a", 5_000, 0, 0, 0))))));

        String json = ProjectJson.toJson(document);

        assertEquals(json.substring(json.indexOf("\"cells\"")), """
            "cells": [
                    {
                      "onset": "00:00:02:000",
                      "settings": { "volume": 1 },
                      "source": {
                        "file": "never-opened.mp4"
                      }
                    },
                    {
                      "onset": "00:00:09:000",
                      "offset": "00:00:14:000",
                      "settings": { "volume": 1 },
                      "source": {
                        "file": "voice.m4a",
                        "duration": "00:00:05:000"
                      }
                    }
                  ]
                }
              ]
            }
            """);
        assertEquals(read(json), document);
        assertEquals(schemaErrors(json), List.of());
    }

    @DataProvider
    public Object[][] invalidFiles() {
        // what is wrong, where the reader reports it, whether the schema also catches it, the file
        return new Object[][] {
            {"wrong format", "format", true,
                change("\"format\": \"watavyu\"", "\"format\": \"datavyu\"")},
            {"newer version", "newer Watavyu", true,
                change("\"version\": 1", "\"version\": 2")},
            {"version as text", "version", true,
                change("\"version\": 1", "\"version\": \"1\"")},
            {"unknown key", "unknown key \"extra\"", true,
                change("\"version\": 1,", "\"version\": 1, \"extra\": 0,")},
            {"unknown column type", "columns[0].type", true,
                change("\"type\": \"annotation\"", "\"type\": \"text\"")},
            {"empty name", "columns[0].name", true,
                change("\"name\": \"A\"", "\"name\": \"\"")},
            {"duplicate column name", "columns[1].name", false,
                change("\"name\": \"V\"", "\"name\": \"A\"")},
            {"values for no codes", "columns[0].cells[0].values", false,
                change("\"codes\": [\"x\"]", "\"codes\": []")},
            {"duplicate code", "columns[0].codes[1]", true,
                change("\"codes\": [\"x\"]", "\"codes\": [\"x\", \"x\"]")},
            {"missing settings", "\"settings\" is missing", true,
                change("\"settings\": { \"hidden\": false },", "")},
            {"hidden not boolean", "columns[0].settings.hidden", true,
                change("\"settings\": { \"hidden\": false }", "\"settings\": { \"hidden\": \"no\" }")},
            {"missing locked", "\"locked\" is missing", true,
                change("\"hidden\": false, \"locked\": false", "\"hidden\": false")},
            {"values per code", "columns[0].cells[0].values", false,
                change("\"values\": [\"v\"]", "\"values\": [\"v\", \"w\"]")},
            {"value not string", "columns[0].cells[0].values[0]", true,
                change("\"values\": [\"v\"]", "\"values\": [1]")},
            {"short hours", "columns[0].cells[0].onset", true,
                change("\"onset\": \"00:00:01:000\"", "\"onset\": \"0:00:01:000\"")},
            {"negative annotation onset", "columns[0].cells[0].onset", true,
                change("\"onset\": \"00:00:01:000\"", "\"onset\": \"-00:00:01:000\"")},
            {"clip offset not onset plus duration", "columns[1].cells[0].offset", false,
                change("\"offset\": \"00:00:10:000\"", "\"offset\": \"00:00:09:000\"")},
            {"negative zero", "columns[1].cells[0].onset", true,
                change("\"onset\": \"00:00:00:000\"", "\"onset\": \"-00:00:00:000\"")},
            {"volume above 1", "columns[1].cells[0].settings.volume", true,
                change("\"volume\": 1", "\"volume\": 1.5")},
            {"zero frame rate", "columns[1].cells[0].source.frameRate", true,
                change("\"frameRate\": 30", "\"frameRate\": 0")},
            {"fractional width", "columns[1].cells[0].source.width", true,
                change("\"width\": 640", "\"width\": 640.5")},
            {"zero width", "columns[1].cells[0].source.width", true,
                change("\"width\": 640", "\"width\": 0")},
            {"offset without a duration", "columns[1].cells[0].offset", false,
                change("\"duration\": \"00:00:10:000\", ", "")},
            {"duration without an offset", "\"offset\" is missing", false,
                change("\"offset\": \"00:00:10:000\",\n", "")},
            {"empty file", "columns[1].cells[0].source.file", true,
                change("\"file\": \"a.mp4\"", "\"file\": \"\"")},
            {"missing file", "\"file\" is missing", true,
                change("\"file\": \"a.mp4\", ", "")},
            {"cells not array", "columns[0].cells: must be an array", true,
                change("[\n        { \"onset\": \"00:00:01:000\", \"offset\": \"00:00:02:000\", "
                    + "\"values\": [\"v\"] }\n      ]", "{}")},
        };
    }

    @Test(dataProvider = "invalidFiles")
    public void readerRejectsInvalidFilesSayingWhere(final String wrong, final String where, final boolean schema,
                                                     final String json)
            throws IOException {
        ProjectFileException e = expectThrows(ProjectFileException.class, () -> read(json));
        assertTrue(e.getMessage().contains(where), e.getMessage());
    }

    /** The schema catches every per-field rule; the rules across fields are the reader's alone. */
    @Test(dataProvider = "invalidFiles")
    public void schemaCatchesExactlyThePerFieldRules(final String wrong, final String where, final boolean schema,
                                                     final String json)
            throws IOException {
        assertEquals(!schemaErrors(json).isEmpty(), schema, wrong);
    }

    @DataProvider
    public Object[][] notProjects() {
        return new Object[][] {
            {"", "empty"},
            {"[]", "the file: must be an object"},
            {"{", "Not valid JSON"},
            {BASE + "{}", "Not valid JSON"},
            {BASE.replace("\"format\": \"watavyu\",", "\"format\": \"watavyu\", \"format\": \"watavyu\","),
                "Not valid JSON"},
        };
    }

    @Test(dataProvider = "notProjects")
    public void readerRejectsFilesThatAreNotProjects(final String text, final String message) {
        ProjectFileException e = expectThrows(ProjectFileException.class, () -> read(text));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    @Test
    public void writerRefusesWhatTheReaderWouldRefuse() {
        ProjectDocument tooManyValues = new ProjectDocument(List.of(
            new AnnotationColumn("A", List.of("x"), false, List.of(new AnnotationCell(10, 15, List.of("a", "b"))))));
        assertTrue(expectThrows(ProjectFileException.class, () -> ProjectJson.toJson(tooManyValues))
            .getMessage().contains("columns[0].cells[0].values"));

        ProjectDocument sameName = new ProjectDocument(List.of(
            new AnnotationColumn("A", List.of("x"), false, List.of()), new MediaColumn("A", false, false, List.of())));
        assertTrue(expectThrows(ProjectFileException.class, () -> ProjectJson.toJson(sameName))
            .getMessage().contains("columns[1].name"));
    }

    @Test
    public void writingAFileReplacesItWholeAndLeavesNothingElse() throws Exception {
        Path folder = Files.createTempDirectory("project");
        Path file = folder.resolve("p" + ProjectDocument.EXTENSION);
        Files.writeString(file, "old contents that are longer than the new ones ".repeat(20));

        ProjectDocument document = new ProjectDocument(List.of(new AnnotationColumn("A", List.of("x"), false, List.of())));
        ProjectJson.write(file, document);

        assertEquals(ProjectJson.read(file), document);
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(files.toList(), List.of(file));
        }
    }

    @Test
    public void aFailedWriteKeepsTheOldFile() throws Exception {
        Path folder = Files.createTempDirectory("project");
        Path file = folder.resolve("p" + ProjectDocument.EXTENSION);
        Files.writeString(file, "old");
        ProjectDocument invalid = new ProjectDocument(List.of(
            new AnnotationColumn("A", List.of(), false, List.of(new AnnotationCell(1, 2, List.of("x"))))));

        expectThrows(ProjectFileException.class, () -> ProjectJson.write(file, invalid));

        assertEquals(Files.readString(file), "old");
        try (Stream<Path> files = Files.list(folder)) {
            assertFalse(files.anyMatch(path -> !path.equals(file)));
        }
    }

    @Test
    public void savingKeepsTheFilesPermissions() throws Exception {
        Path file = Files.createTempDirectory("project").resolve("p" + ProjectDocument.EXTENSION);
        ProjectDocument document = new ProjectDocument(List.of());

        ProjectJson.write(file, document);
        assertEquals(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)), "rw-r--r--");

        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw----"));
        ProjectJson.write(file, document);
        assertEquals(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)), "rw-rw----");
    }

    @Test
    public void savingThroughALinkKeepsTheLink() throws Exception {
        Path folder = Files.createTempDirectory("project");
        Path real = folder.resolve("real" + ProjectDocument.EXTENSION);
        Path link = Files.createSymbolicLink(folder.resolve("link" + ProjectDocument.EXTENSION), real);
        ProjectDocument document = new ProjectDocument(List.of(new AnnotationColumn("A", List.of("x"), false,
            List.of())));
        ProjectJson.write(real, new ProjectDocument(List.of()));

        ProjectJson.write(link, document);

        assertTrue(Files.isSymbolicLink(link));
        assertEquals(ProjectJson.read(real), document);
    }
}

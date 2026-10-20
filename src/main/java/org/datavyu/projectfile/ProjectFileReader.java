package org.datavyu.projectfile;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.datavyu.projectfile.ProjectDocument.Clip;
import org.datavyu.projectfile.ProjectDocument.AnnotationCell;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.datavyu.projectfile.ProjectDocument.Column;
import org.datavyu.projectfile.ProjectDocument.Source;
import org.datavyu.projectfile.ProjectDocument.MediaColumn;
import org.datavyu.util.Timestamp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns a project file's contents into a document, checking its keys and types. An unknown or missing key or a value
 * of the wrong type is an error naming its place in the file. {@link ProjectRules} checks the result.
 */
final class ProjectFileReader {

    static final String ANNOTATION = "annotation";
    static final String MEDIA = "media";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ProjectFileReader() {
    }

    /**
     * Reads a project file's contents.
     *
     * @param in a project file's contents, UTF-8
     * @return the project it holds, not yet checked against the rules
     * @throws ProjectFileException if the contents are not JSON or have the wrong keys or types
     * @throws IOException          if they cannot be read
     */
    static ProjectDocument read(final InputStream in) throws ProjectFileException, IOException {
        JsonNode root;
        try {
            root = MAPPER.readTree(in);
        } catch (JsonProcessingException e) {
            throw new ProjectFileException("Not valid JSON: " + e.getOriginalMessage(), e);
        }
        if (root == null || root.isMissingNode()) {
            throw new ProjectFileException("The file is empty.");
        }
        return project(root);
    }


    private static ProjectDocument project(final JsonNode root) throws ProjectFileException {
        JsonFields.keys(root, "the file", "format", "version", "columns");
        String format = JsonFields.text(root.get("format"), "format");
        if (!ProjectDocument.FORMAT.equals(format)) {
            throw new ProjectFileException("format: \"" + format + "\" is not a Watavyu project");
        }
        JsonNode version = root.get("version");
        if (!version.isIntegralNumber()) {
            throw new ProjectFileException("version: must be a whole number");
        }
        if (version.asLong() != ProjectDocument.VERSION) {
            throw new ProjectFileException("version: " + version.asText()
                + (version.asLong() > ProjectDocument.VERSION
                    ? " was written by a newer Watavyu" : " is not a version Watavyu writes"));
        }
        List<Column> columns = new ArrayList<>();
        JsonNode array = JsonFields.array(root.get("columns"), "columns");
        for (int i = 0; i < array.size(); i++) {
            columns.add(column(array.get(i), "columns[" + i + "]"));
        }
        return new ProjectDocument(columns);
    }

    private static Column column(final JsonNode node, final String at) throws ProjectFileException {
        JsonFields.object(node, at);
        JsonNode type = node.get("type");
        if (type == null) {
            throw new ProjectFileException(at + ": \"type\" is missing");
        }
        String kind = JsonFields.text(type, at + ".type");
        if (ANNOTATION.equals(kind)) {
            JsonFields.keys(node, at, "name", "type", "codes", "settings", "cells");
            JsonNode settings = node.get("settings");
            JsonFields.keys(settings, at + ".settings", "hidden");
            List<String> codes = JsonFields.texts(node.get("codes"), at + ".codes");
            List<AnnotationCell> cells = new ArrayList<>();
            JsonNode array = JsonFields.array(node.get("cells"), at + ".cells");
            for (int i = 0; i < array.size(); i++) {
                cells.add(annotationCell(array.get(i), at + ".cells[" + i + "]"));
            }
            return new AnnotationColumn(JsonFields.text(node.get("name"), at + ".name"), codes,
                JsonFields.bool(settings.get("hidden"), at + ".settings.hidden"), cells);
        }
        if (MEDIA.equals(kind)) {
            JsonFields.keys(node, at, "name", "type", "settings", "cells");
            JsonNode settings = node.get("settings");
            JsonFields.keys(settings, at + ".settings", "hidden", "locked");
            List<Clip> clips = new ArrayList<>();
            JsonNode array = JsonFields.array(node.get("cells"), at + ".cells");
            for (int i = 0; i < array.size(); i++) {
                clips.add(clip(array.get(i), at + ".cells[" + i + "]"));
            }
            return new MediaColumn(JsonFields.text(node.get("name"), at + ".name"),
                JsonFields.bool(settings.get("hidden"), at + ".settings.hidden"),
                JsonFields.bool(settings.get("locked"), at + ".settings.locked"), clips);
        }
        throw new ProjectFileException(at + ".type: \"" + kind + "\" is not \"annotation\" or \"media\"");
    }

    private static AnnotationCell annotationCell(final JsonNode node, final String at) throws ProjectFileException {
        JsonFields.keys(node, at, "onset", "offset", "values");
        return new AnnotationCell(JsonFields.time(node.get("onset"), at + ".onset"), JsonFields.time(node.get("offset"), at + ".offset"),
            JsonFields.texts(node.get("values"), at + ".values"));
    }

    /**
     * Reads a clip. What the source says about its media file is optional, as the file may never have opened; a clip
     * has an offset exactly when its source has a duration.
     */
    private static Clip clip(final JsonNode node, final String at) throws ProjectFileException {
        JsonFields.keys(node, at, Set.of("offset"), "onset", "offset", "settings", "source");
        JsonNode settings = node.get("settings");
        JsonFields.keys(settings, at + ".settings", "volume");
        JsonNode sourceNode = node.get("source");
        JsonFields.keys(sourceNode, at + ".source", Set.of("duration", "frameRate", "width", "height"),
            "file", "duration", "frameRate", "width", "height");
        Source source = new Source(JsonFields.text(sourceNode.get("file"), at + ".source.file"),
            sourceNode.has("duration") ? JsonFields.time(sourceNode.get("duration"), at + ".source.duration") : 0,
            sourceNode.has("frameRate") ? positive(JsonFields.number(sourceNode.get("frameRate"),
                at + ".source.frameRate"), at + ".source.frameRate") : 0,
            sourceNode.has("width") ? (int) positive(JsonFields.integer(sourceNode.get("width"),
                at + ".source.width"), at + ".source.width") : 0,
            sourceNode.has("height") ? (int) positive(JsonFields.integer(sourceNode.get("height"),
                at + ".source.height"), at + ".source.height") : 0);
        Clip clip = new Clip(JsonFields.time(node.get("onset"), at + ".onset"),
            JsonFields.number(settings.get("volume"), at + ".settings.volume"), source);
        if (sourceNode.has("duration") != node.has("offset")) {
            throw new ProjectFileException(at + (node.has("offset")
                ? ".offset: must be left out when the source's duration is unknown"
                : ": \"offset\" is missing"));
        }
        if (node.has("offset")) {
            long offset = JsonFields.time(node.get("offset"), at + ".offset");
            long expected;
            try {
                expected = Math.addExact(clip.onset(), source.duration());
            } catch (ArithmeticException e) {
                throw new ProjectFileException(at + ".offset: out of range", e);
            }
            if (offset != expected) {
                throw new ProjectFileException(at + ".offset: must be the onset plus the source's duration ("
                    + Timestamp.format(expected) + ")");
            }
        }
        return clip;
    }

    private static double positive(final double value, final String at) throws ProjectFileException {
        if (!(value > 0)) {
            throw new ProjectFileException(at + ": must be more than 0");
        }
        return value;
    }
}

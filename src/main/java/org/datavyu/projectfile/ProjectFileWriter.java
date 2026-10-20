package org.datavyu.projectfile;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import org.datavyu.projectfile.ProjectDocument.Clip;
import org.datavyu.projectfile.ProjectDocument.AnnotationCell;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.datavyu.projectfile.ProjectDocument.Column;
import org.datavyu.projectfile.ProjectDocument.Source;
import org.datavyu.projectfile.ProjectDocument.MediaColumn;
import org.datavyu.util.Timestamp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Lays out a project file: two-space indent, keys in the order of the schema, cells in time order, one line per
 * annotation cell and a trailing newline. It writes whatever it is given; {@link ProjectRules} checks a document first.
 */
final class ProjectFileWriter {

    private static final Comparator<AnnotationCell> CELL_ORDER =
        Comparator.comparingLong(AnnotationCell::onset).thenComparingLong(AnnotationCell::offset);
    private static final Comparator<Clip> CLIP_ORDER = Comparator.comparingLong(Clip::onset);

    private ProjectFileWriter() {
    }

    /**
     * Formats a project as a project file's contents.
     *
     * @param document a project that follows the rules
     * @return its project file's contents
     */
    static String toJson(final ProjectDocument document) {
        StringBuilder json = new StringBuilder();
        json.append("{\n")
            .append("  \"format\": ").append(string(ProjectDocument.FORMAT)).append(",\n")
            .append("  \"version\": ").append(ProjectDocument.VERSION).append(",\n")
            .append("  \"columns\": [");
        Iterator<Column> columns = document.columns().iterator();
        if (columns.hasNext()) {
            json.append('\n');
            while (columns.hasNext()) {
                Column column = columns.next();
                if (column instanceof AnnotationColumn annotation) {
                    writeAnnotation(json, annotation);
                } else if (column instanceof MediaColumn media) {
                    writeMedia(json, media);
                }
                json.append(columns.hasNext() ? ",\n" : "\n");
            }
            json.append("  ");
        }
        return json.append("]\n}\n").toString();
    }

    private static void writeAnnotation(final StringBuilder json, final AnnotationColumn column) {
        json.append("    {\n")
            .append("      \"name\": ").append(string(column.name())).append(",\n")
            .append("      \"type\": \"").append(ProjectFileReader.ANNOTATION).append("\",\n")
            .append("      \"codes\": ").append(strings(column.codes())).append(",\n")
            .append("      \"settings\": { \"hidden\": ").append(column.hidden()).append(" },\n")
            .append("      \"cells\": [");
        List<AnnotationCell> cells = new ArrayList<>(column.cells());
        cells.sort(CELL_ORDER);
        for (int i = 0; i < cells.size(); i++) {
            AnnotationCell cell = cells.get(i);
            json.append(i == 0 ? "\n" : ",\n")
                .append("        { \"onset\": \"").append(Timestamp.format(cell.onset()))
                .append("\", \"offset\": \"").append(Timestamp.format(cell.offset()))
                .append("\", \"values\": ").append(strings(cell.values())).append(" }");
        }
        json.append(cells.isEmpty() ? "]\n" : "\n      ]\n").append("    }");
    }

    private static void writeMedia(final StringBuilder json, final MediaColumn column) {
        json.append("    {\n")
            .append("      \"name\": ").append(string(column.name())).append(",\n")
            .append("      \"type\": \"").append(ProjectFileReader.MEDIA).append("\",\n")
            .append("      \"settings\": { \"hidden\": ").append(column.hidden())
            .append(", \"locked\": ").append(column.locked()).append(" },\n")
            .append("      \"cells\": [");
        List<Clip> clips = new ArrayList<>(column.clips());
        clips.sort(CLIP_ORDER);
        for (int i = 0; i < clips.size(); i++) {
            Clip clip = clips.get(i);
            Source source = clip.source();
            json.append(i == 0 ? "\n" : ",\n")
                .append("        {\n")
                .append("          \"onset\": \"").append(Timestamp.format(clip.onset())).append("\",\n");
            if (source.duration() > 0) {
                json.append("          \"offset\": \"").append(Timestamp.format(clip.offset())).append("\",\n");
            }
            json.append("          \"settings\": { \"volume\": ").append(number(clip.volume())).append(" },\n")
                .append("          \"source\": {\n");
            // What the media file said, when it has opened; a detail it never gave is left out.
            List<String> fields = new ArrayList<>();
            fields.add("\"file\": " + string(source.file()));
            if (source.duration() > 0) {
                fields.add("\"duration\": \"" + Timestamp.format(source.duration()) + "\"");
            }
            if (source.frameRate() > 0) {
                fields.add("\"frameRate\": " + number(source.frameRate()));
            }
            if (source.width() > 0) {
                fields.add("\"width\": " + source.width());
            }
            if (source.height() > 0) {
                fields.add("\"height\": " + source.height());
            }
            json.append("            ").append(String.join(",\n            ", fields)).append('\n')
                .append("          }\n")
                .append("        }");
        }
        json.append(clips.isEmpty() ? "]\n" : "\n      ]\n").append("    }");
    }

    private static String string(final String value) {
        return '"' + new String(JsonStringEncoder.getInstance().quoteAsString(value)) + '"';
    }

    private static String strings(final List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            json.append(i == 0 ? "" : ", ").append(string(values.get(i)));
        }
        return json.append(']').toString();
    }

    private static String number(final double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}

package org.datavyu.projectfile;

import java.util.List;

/**
 * A Watavyu project as stored in a {@code .watavyu.json} file: its columns in spreadsheet order. {@link ProjectJson}
 * reads and writes it; the file format is described by {@code watavyu-project.schema.json} next to this class and in
 * AGENTS.md › Decisions.
 *
 * @param columns the columns, in spreadsheet order
 */
public record ProjectDocument(List<Column> columns) {

    /** The {@code format} every project file names. */
    public static final String FORMAT = "watavyu";

    /** The {@code version} of the format this class reads and writes. */
    public static final int VERSION = 1;

    /** The extension of project files. */
    public static final String EXTENSION = ".watavyu.json";

    public ProjectDocument {
        columns = List.copyOf(columns);
    }

    /** A column of the spreadsheet: annotation cells or media clips. */
    public sealed interface Column permits AnnotationColumn, MediaColumn {

        /** Returns the column's name, unique in the project. */
        String name();
    }

    /**
     * A column whose cells hold one value per code.
     *
     * @param name   the column's name
     * @param codes  the names of its codes, in order; none when the column holds timing only
     * @param hidden whether the column is hidden in the spreadsheet
     * @param cells  its cells
     */
    public record AnnotationColumn(String name, List<String> codes, boolean hidden, List<AnnotationCell> cells)
            implements Column {

        public AnnotationColumn {
            codes = List.copyOf(codes);
            cells = List.copyOf(cells);
        }
    }

    /**
     * An annotation cell. New cells are point cells; an offset before the onset is allowed, and the spreadsheet
     * flags it.
     *
     * @param onset  the cell's start in milliseconds
     * @param offset the cell's end in milliseconds
     * @param values one value per code of its column, {@code ""} when empty
     */
    public record AnnotationCell(long onset, long offset, List<String> values) {

        public AnnotationCell {
            values = List.copyOf(values);
        }
    }

    /**
     * A column that is one media window. Each clip shows its source from the clip's onset for the source's duration;
     * gaps show blank frames, and where clips overlap the later one wins.
     *
     * @param name   the column's name
     * @param hidden whether the media window is hidden
     * @param locked whether the clips are protected from being dragged
     * @param clips  its clips
     */
    public record MediaColumn(String name, boolean hidden, boolean locked, List<Clip> clips) implements Column {

        public MediaColumn {
            clips = List.copyOf(clips);
        }
    }

    /**
     * A media clip. It may start before time zero, when a recording began before the annotated session. This is a
     * stopgap while the separate video controller window exists (AGENTS.md › Decisions).
     *
     * @param onset  where the clip starts on the timeline, in milliseconds
     * @param volume its volume, from 0 to 1
     * @param source the media it shows
     */
    public record Clip(long onset, double volume, Source source) {

        /** Returns where the clip ends: its onset plus its source's duration. */
        public long offset() {
            return onset + source.duration();
        }
    }

    /**
     * A media file and what Watavyu last read from it. The file wins when they disagree.
     *
     * @param file      the file's path: relative to the project file's folder, or absolute
     * @param duration  its duration in milliseconds; 0 if unknown
     * @param frameRate its nominal frame rate in frames per second; 0 if unknown or it has no video
     * @param width     its width in pixels; 0 if unknown or it has no video
     * @param height    its height in pixels; 0 if unknown or it has no video
     */
    public record Source(String file, long duration, double frameRate, int width, int height) {
    }
}

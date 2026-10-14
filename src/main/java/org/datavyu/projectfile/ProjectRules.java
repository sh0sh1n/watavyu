package org.datavyu.projectfile;

import org.datavyu.projectfile.ProjectDocument.Clip;
import org.datavyu.projectfile.ProjectDocument.AnnotationCell;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.datavyu.projectfile.ProjectDocument.Column;
import org.datavyu.projectfile.ProjectDocument.Source;
import org.datavyu.projectfile.ProjectDocument.MediaColumn;

import java.util.HashSet;
import java.util.Set;

/** The rules of a project file that hold across fields, checked after reading and before writing. */
final class ProjectRules {

    private ProjectRules() {
    }

    /**
     * Checks a project against the rules.
     *
     * @param document a project
     * @throws ProjectFileException naming the first place that breaks a rule
     */
    static void check(final ProjectDocument document) throws ProjectFileException {
        Set<String> names = new HashSet<>();
        for (int c = 0; c < document.columns().size(); c++) {
            Column column = document.columns().get(c);
            String at = "columns[" + c + "]";
            require(column.name() != null && !column.name().isEmpty(), at + ".name", "must not be empty");
            require(names.add(column.name()), at + ".name", "\"" + column.name() + "\" is used by another column");
            if (column instanceof AnnotationColumn annotation) {
                validateAnnotation(annotation, at);
            } else if (column instanceof MediaColumn media) {
                validateMedia(media, at);
            }
        }
    }

    private static void validateAnnotation(final AnnotationColumn column, final String at) throws ProjectFileException {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < column.codes().size(); i++) {
            String code = column.codes().get(i);
            require(code != null && !code.isEmpty(), at + ".codes[" + i + "]", "must not be empty");
            require(codes.add(code), at + ".codes[" + i + "]", "\"" + code + "\" is named twice");
        }
        for (int i = 0; i < column.cells().size(); i++) {
            AnnotationCell cell = column.cells().get(i);
            String cellAt = at + ".cells[" + i + "]";
            require(cell.onset() >= 0, cellAt + ".onset", "must not be negative");
            require(cell.values().size() == column.codes().size(), cellAt + ".values",
                "must hold one value per code (" + column.codes().size() + ")");
            for (int v = 0; v < cell.values().size(); v++) {
                require(cell.values().get(v) != null, cellAt + ".values[" + v + "]", "must be a string");
            }
        }
    }

    private static void validateMedia(final MediaColumn column, final String at) throws ProjectFileException {
        for (int i = 0; i < column.clips().size(); i++) {
            Clip clip = column.clips().get(i);
            Source source = clip.source();
            String clipAt = at + ".cells[" + i + "]";
            require(clip.volume() >= 0 && clip.volume() <= 1, clipAt + ".settings.volume", "must be from 0 to 1");
            require(source.file() != null && !source.file().isEmpty(), clipAt + ".source.file", "must not be empty");
            require(source.duration() >= 0, clipAt + ".source.duration", "must not be negative");
            require(Double.isFinite(source.frameRate()) && source.frameRate() > 0, clipAt + ".source.frameRate",
                "must be more than 0");
            require(source.width() > 0, clipAt + ".source.width", "must be more than 0");
            require(source.height() > 0, clipAt + ".source.height", "must be more than 0");
            try {
                Math.addExact(clip.onset(), source.duration());
            } catch (ArithmeticException e) {
                throw new ProjectFileException(clipAt + ".offset: out of range", e);
            }
        }
    }

    private static void require(final boolean rule, final String at, final String message)
            throws ProjectFileException {
        if (!rule) {
            throw new ProjectFileException(at + ": " + message);
        }
    }
}

package org.datavyu.projectfile;

import org.datavyu.models.db.Argument;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.MediaClip;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.datavyu.projectfile.ProjectDocument.AnnotationCell;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.datavyu.projectfile.ProjectDocument.Clip;
import org.datavyu.projectfile.ProjectDocument.Column;
import org.datavyu.projectfile.ProjectDocument.MediaColumn;
import org.datavyu.projectfile.ProjectDocument.Source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts between the spreadsheet's columns and a project file's columns. An annotation column holds its codes, in
 * order, and its cells' values, one for each code. A media column holds its clips; the spreadsheet holds their files'
 * paths absolute, and the project file relative to its folder where it can.
 */
public final class SpreadsheetColumns {

    private SpreadsheetColumns() {
    }

    /**
     * Reads all of a spreadsheet's columns.
     *
     * @param dataStore     a spreadsheet
     * @param projectFolder the folder the project file is saved in
     * @return its columns in spreadsheet order, with their cells in time order
     */
    public static List<Column> columns(final DataStore dataStore, final Path projectFolder) {
        List<Column> columns = new ArrayList<>();
        for (Variable variable : dataStore.getAllVariables()) {
            columns.add(variable.isMedia() ? media(variable, projectFolder) : annotation(variable));
        }
        return columns;
    }

    /**
     * Reads a spreadsheet's annotation columns.
     *
     * @param dataStore a spreadsheet
     * @return its annotation columns in spreadsheet order, with their cells in time order
     */
    public static List<AnnotationColumn> of(final DataStore dataStore) {
        List<AnnotationColumn> columns = new ArrayList<>();
        for (Variable variable : dataStore.getAllVariables()) {
            if (!variable.isMedia()) {
                columns.add(annotation(variable));
            }
        }
        return columns;
    }

    /** A media column, with what is known of each clip's file; a clip whose file never opened is saved all the same. */
    private static MediaColumn media(final Variable variable, final Path projectFolder) {
        List<Clip> clips = new ArrayList<>();
        for (Cell cell : variable.getCellsTemporally()) {
            MediaClip clip = cell.getClip();
            clips.add(new Clip(cell.getOnset(), clip.volume(), new Source(
                ProjectPaths.stored(projectFolder, Path.of(clip.file())), clip.duration(), clip.frameRate(),
                clip.width(), clip.height())));
        }
        return new MediaColumn(variable.getName(), variable.isHidden(), variable.isLocked(), clips);
    }

    private static AnnotationColumn annotation(final Variable variable) {
        List<String> codes = new ArrayList<>();
        variable.getRootNode().childArguments.forEach(code -> codes.add(code.name));
        List<AnnotationCell> cells = new ArrayList<>();
        for (Cell cell : variable.getCellsTemporally()) {
            List<String> values = new ArrayList<>();
            for (int i = 0; i < codes.size(); i++) {
                values.add(cell.getMatrixValue(i).toString());
            }
            cells.add(new AnnotationCell(cell.getOnset(), cell.getOffset(), values));
        }
        return new AnnotationColumn(variable.getName(), codes, variable.isHidden(), variable.isLocked(), cells);
    }

    /**
     * Adds a project file's columns to a spreadsheet, after any it already has.
     *
     * @param dataStore     the spreadsheet
     * @param columns       the columns to add, in order
     * @param projectFolder the folder of the project file they came from
     * @throws UserWarningException if the spreadsheet already has a column of the same name
     */
    public static void addColumns(final DataStore dataStore, final List<Column> columns, final Path projectFolder)
            throws UserWarningException {
        for (Column column : columns) {
            if (column instanceof AnnotationColumn annotation) {
                addTo(dataStore, List.of(annotation));
            } else if (column instanceof MediaColumn media) {
                Variable variable = dataStore.createMediaVariable(media.name());
                variable.setHidden(media.hidden());
                variable.setOrderIndex(dataStore.getAllVariables().size() - 1);
                for (Clip clip : media.clips()) {
                    Source source = clip.source();
                    variable.createClip(clip.onset(), new MediaClip(
                        ProjectPaths.resolve(projectFolder, source.file()).toString(), source.duration(),
                        source.frameRate(), source.width(), source.height(), clip.volume()));
                }
                variable.setLocked(media.locked());
            }
        }
    }

    /**
     * Adds columns to a spreadsheet, after any it already has. Names that the spreadsheet would refuse for a new
     * column are kept, as they are for columns from older Datavyu files.
     *
     * @param dataStore the spreadsheet
     * @param columns   the columns to add, in order
     * @throws UserWarningException if the spreadsheet already has a column of the same name
     */
    public static void addTo(final DataStore dataStore, final List<AnnotationColumn> columns) throws UserWarningException {
        int order = dataStore.getAllVariables().size();
        for (AnnotationColumn column : columns) {
            Variable variable = dataStore.createVariable(column.name(), true);
            variable.setHidden(column.hidden());
            variable.setOrderIndex(order);
            order++;
            Argument root = variable.getRootNode();
            root.clearChildArguments();
            for (String code : column.codes()) {
                root.childArguments.add(new Argument(code));
            }
            variable.setRootNode(root);
            for (AnnotationCell annotated : column.cells()) {
                Cell cell = variable.createCell();
                cell.setOnset(annotated.onset());
                cell.setOffset(annotated.offset());
                for (int i = 0; i < annotated.values().size(); i++) {
                    cell.setMatrixValue(i, annotated.values().get(i));
                }
            }
            // Locked last, once its cells are in.
            variable.setLocked(column.locked());
        }
    }
}

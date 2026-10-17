package org.datavyu.projectfile;

import org.datavyu.models.db.Argument;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.datavyu.projectfile.ProjectDocument.AnnotationCell;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between the spreadsheet's columns and a project file's annotation columns: a column's codes, in order, and
 * its cells' values, one for each code.
 */
public final class SpreadsheetColumns {

    private SpreadsheetColumns() {
    }

    /**
     * Reads a spreadsheet's columns.
     *
     * @param dataStore a spreadsheet
     * @return its columns in spreadsheet order, with their cells in time order
     */
    public static List<AnnotationColumn> of(final DataStore dataStore) {
        List<AnnotationColumn> columns = new ArrayList<>();
        for (Variable variable : dataStore.getAllVariables()) {
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
            columns.add(new AnnotationColumn(variable.getName(), codes, variable.isHidden(), cells));
        }
        return columns;
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
        }
    }
}

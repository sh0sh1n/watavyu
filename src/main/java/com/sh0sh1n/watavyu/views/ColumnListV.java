/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sh0sh1n.watavyu.views;

import com.sh0sh1n.watavyu.util.Resources;
import net.miginfocom.swing.MigLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sh0sh1n.watavyu.controllers.project.ColumnCommands;
import com.sh0sh1n.watavyu.controllers.project.ProjectController;
import com.sh0sh1n.watavyu.models.db.DataStore;
import com.sh0sh1n.watavyu.models.db.DataStoreListener;
import com.sh0sh1n.watavyu.models.db.Column;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The dialog to list database columns.
 */
public final class ColumnListV extends JDialog
        implements TableModelListener, DataStoreListener {

    /**
     * The column for if a column is visible or not.
     */
    private static final int VCOLUMN = 1;
    /**
     * The column for a columns name.
     */
    private static final int NCOLUMN = 2;
    /**
     * The column for order index of column
     */
    private static final int OCOLUMN = 0;
    /**
     * The total number of columns in the columns list.
     */
    private static final int TOTAL_COLUMNS = 3;
    /**
     * The logger for this class.
     */
    private static Logger LOGGER = LogManager.getLogger(ColumnListV.class);
    /**
     * DataStore for holding all the information in the column list.
     */
    private DataStore dataStore;

    /**
     * The table model of the JTable that lists the actual columns.
     */
    private VListTableModel tableModel;

    /**
     * Visual components for the dialog.
     */
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable columnList;
    private JButton showAllButton;

    /** Shows and hides the spreadsheet's columns. */
    private final ColumnCommands columns;

    /**
     * Mapping between database column identifier - to the table model.
     */
    private Map<Column, Integer> dbToTableMap;

    /**
     * Resource map holding all the string information.
     */
    private Resources rMap = Resources.of(ColumnListV.class);

    /**
     * Creates new form ListColumnsView.
     *
     * @param parent     The parent frame for this dialog.
     * @param modal      Is this dialog to be modal (true), or not.
     * @param controller The spreadsheet whose columns it lists.
     */
    public ColumnListV(final java.awt.Frame parent,
                         final boolean modal,
                         final ProjectController controller) {
        super(parent, modal);
        this.columns = controller.columns();
        tableModel = new VListTableModel();
        dbToTableMap = new HashMap<>();
        initComponents();
        setName(this.getClass().getSimpleName());
        dataStore = controller.getDataStore();

        // Set the names of the columns.
        tableModel.addColumn(rMap.getString("Table.orderColumn"));
        tableModel.addColumn(rMap.getString("Table.visibleColumn"));
        tableModel.addColumn(rMap.getString("Table.nameColumn"));
        
        //fix visible and ordering columns to exact size, let last one grow with table
        columnList.getColumnModel().getColumn(VCOLUMN).setMinWidth(60);
        columnList.getColumnModel().getColumn(VCOLUMN).setMaxWidth(60);    
        columnList.getColumnModel().getColumn(OCOLUMN).setMinWidth(40);
        columnList.getColumnModel().getColumn(OCOLUMN).setMaxWidth(40);
        columnList.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);


        populateTable();
    }
    
    @SuppressWarnings("unchecked")
    private void initComponents() {
        jScrollPane1 = new javax.swing.JScrollPane();
        columnList = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(rMap.getString("columnListDialog.title"));
        setName("columnListDialog"); 

        jScrollPane1.setName("jScrollPane1"); 

        columnList.setModel(tableModel);
        columnList.setMinimumSize(new java.awt.Dimension(400, 200));
        columnList.setName("columnList");
        columnList.setAutoCreateRowSorter(true);
        jScrollPane1.setViewportView(columnList);

        showAllButton = new JButton(rMap.getString("showAllButton.text"));
        showAllButton.setName("showAllColumns");
        showAllButton.addActionListener(e -> showAllColumns());
        

       

        MigLayout layout = new MigLayout("wrap, novisualpadding");
        getContentPane().setLayout(layout);
        getContentPane().add(jScrollPane1);
        getContentPane().add(showAllButton);

        pack();
    }

    /** Shows every hidden column of the spreadsheet, and their media windows. */
    private void showAllColumns() {
        LOGGER.info("Showing all columns");
        columns.showAll();
    }

    public void populateTable() {
        for (Column var : dataStore.getAllColumns()) {
            insertRow(var, rMap);
        }
    }

    /** @return the column shown in a row of the table, or null */
    private Column columnAtRow(final int row) {
        for (Map.Entry<Column, Integer> entry : dbToTableMap.entrySet()) {
            if (entry.getValue() == row) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void recreateMap() {
        dbToTableMap.clear();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            dbToTableMap.put(dataStore.getColumn((String) tableModel.getValueAt(i, NCOLUMN)), i);
        }
    }

    /**
     * Registers this as a listener for all the stuff that we need to be
     * notified about when things change in the UserInterface.
     */
    public void registerListeners() {
        tableModel.addTableModelListener(this);
        dataStore.addListener(this);
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        int row = e.getFirstRow();
        int column = e.getColumn();

        if (row >= 0 && column >= 0) {
            TableModel model = (TableModel) e.getSource();
            String columnName = model.getColumnName(column);
            Object data = model.getValueAt(row, column);

            Column var = columnAtRow(row);

            if (columnName.equals(rMap.getString("Table.visibleColumn"))) {
                LOGGER.info("Editied Column Visbility from ColumnList");

                if (var.isHidden() == (Boolean) data) {
                    if ((Boolean) data) {
                        columns.show(var);
                    } else {
                        columns.hide(List.of(var));
                    }
                }
            }
        }
    }

    /**
     * Inserts a row at the end of the column list.
     *
     * @param dataColumn The column to add to the list.
     * @param rMap     The resource map - used to find localised string bundles
     */
    public void insertRow(final Column dataColumn, final Resources rMap) {
        insertRow(dataColumn, rMap, -1);
    }

    /**
     * Add a new row to the column list.
     *
     * @param dataColumn The Column being added to the list.
     * @param rMap     The resource map - used to find localised string bundles.
     * @param position The position to place the new row.
     */
    public void insertRow(final Column dataColumn,
                          final Resources rMap,
                          int position) {
        Object[] vals = new Object[TOTAL_COLUMNS];

        vals[VCOLUMN] = !dataColumn.isHidden();
        vals[NCOLUMN] = dataColumn.getName();

        // Add new row to table model.
        int rowId = tableModel.getRowCount();
        if ((position >= 0) && (position <= tableModel.getRowCount())) {
            rowId = position;
        }
        vals[OCOLUMN] = rowId + 1;

        tableModel.insertRow(rowId, vals);
        dbToTableMap.put(dataColumn, rowId);
    }

    @Override
    public void columnAdded(final Column newColumn) {
        // Undo puts a deleted column back in its place, not always last.
        columnOrderChanged();
    }

    @Override
    public void columnRemoved(final Column deletedColumn) {
        tableModel.removeRow(dbToTableMap.get(deletedColumn));
        recreateMap();
    }

    @Override
    public void columnOrderChanged() {
        tableModel.setRowCount(0);
        dbToTableMap.clear();
        populateTable();
    }

    @Override
    public void columnHidden(final Column hiddenColumn) {
        tableModel.setValueAt(!hiddenColumn.isHidden(),
                dbToTableMap.get(hiddenColumn),
                VCOLUMN);
    }

    @Override
    public void columnVisible(final Column visibleColumn) {
        tableModel.setValueAt(!visibleColumn.isHidden(),
                dbToTableMap.get(visibleColumn),
                VCOLUMN);
    }

    @Override
    public void columnNameChange(final Column editedColumn) {
        tableModel.setValueAt(editedColumn.getName(),
                dbToTableMap.get(editedColumn),
                NCOLUMN);
    }

    private static class VListTableModel extends DefaultTableModel {
        @Override
        public Class<?> getColumnClass(int column) {
            if (column == VCOLUMN) {
                return Boolean.class;
            }
            return Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            // Columns are renamed in the Code Editor, not here.
            return column == VCOLUMN;
        }
    }
}

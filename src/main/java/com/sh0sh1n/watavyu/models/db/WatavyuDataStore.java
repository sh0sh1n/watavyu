/*
 * Copyright (c) 2011 Datavyu Foundation, http://datavyu.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sh0sh1n.watavyu.models.db;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO: Fill in the comment at the ???
 *
 * Acts as a connector between Watavyu's various data structures and a hash map that serves as key value store.
 */
public class WatavyuDataStore implements DataStore {


    /** Told when this spreadsheet is changed or saved, so its title can show it. */
    private TitleNotifier titleNotifier = null;

    /** Name of the DataStore - does not need to persist - is used for file names. */
    private String name = "untitled";

    /** Has the DataStore changed since it has last been marked as not changed. */
    private boolean changed;

    /** All listeners of this data store */
    private List<DataStoreListener> dataStoreListeners = new ArrayList<>();

    /** The columns by name. */
    private final Map<String, Variable> variables = new HashMap<>();

    /** The columns in spreadsheet order, which this keeps; views follow it. */
    private final List<Variable> order = new ArrayList<>();

    public WatavyuDataStore() {
        changed = false;
    }

    @Override
    public void markAsChanged() {
        if (!changed) {
            changed = true;
            if (this.titleNotifier != null) {
                this.titleNotifier.updateTitle();
            }
        }
    }

    @Override
    public List<Variable> getAllVariables() {
        return new ArrayList<>(order);
    }

    @Override
    public int indexOf(final Variable column) {
        return order.indexOf(column);
    }

    @Override
    public List<Variable> getVisibleVariables() {
        List<Variable> allVariables = getAllVariables();
        List<Variable> visibleVariables = new ArrayList<>();
        for (Variable variable : allVariables) {
            if (!variable.isHidden()) {
                visibleVariables.add(variable);
            }
        }
        return visibleVariables;
    }

    @Override
    public List<Variable> getSelectedVariables() {
        return order.stream().filter(Variable::isSelected).toList();
    }

    @Override
    public void clearVariableSelection() {
        for (Variable variable : order) {
            variable.setSelected(false);
        }
    }

    @Override
    public List<Cell> getSelectedCells() {
        List<Cell> selectedCells = new ArrayList<>();
        for (Variable variable : order) {
            for (Cell cell : variable.getCells()) {
                if (cell.isSelected()) {
                    selectedCells.add(cell);
                }
            }
        }
        return selectedCells;
    }

    @Override
    public void clearCellSelection() {
        for (Variable variable : order) {
            for (Cell cell : variable.getCells()) {
                if (cell.isSelected()) {
                    cell.setSelected(false);
                }
                if (cell.isHighlighted()) {
                    cell.setHighlighted(false);
                }
            }
        }
    }

    @Override
    public void deselectAll() {
        this.clearCellSelection();
        this.clearVariableSelection();
    }

    @Override
    public Variable getVariable(String varName) {
        return variables.get(varName);
    }

    @Override
    public Variable createVariable(final String name) throws UserWarningException {
        return createVariable(name, false);
    }

    @Override
    public Variable createVariable(final String name, boolean grandfathered)
            throws UserWarningException {
        return addNewVariable(name, grandfathered, false);
    }

    @Override
    public Variable createMediaVariable(final String name) throws UserWarningException {
        return addNewVariable(name, true, true);
    }

    private Variable addNewVariable(final String name, final boolean grandfathered,
                                    final boolean media) throws UserWarningException {
        // Check to make sure the variable name is not already in use:
        Variable varTest = getVariable(name);
        if (varTest != null) {
            throw new UserWarningException("Unable to add column with name '" + name
                    + "', one with the same name already exists.");
        }

        Variable v = new WatavyuVariable(name, grandfathered, this, media);
        addVariable(v, order.size());
        return v;
    }

    @Override
    public void removeVariable(final Variable var) {
        if (!order.remove(var)) {
            return;
        }
        variables.remove(var.getName());
        markAsChanged();
        for (DataStoreListener listener : List.copyOf(dataStoreListeners)) {
            listener.variableRemoved(var);
        }
    }

    @Override
    public void addVariable(final Variable var, final int index) {
        if (variables.containsKey(var.getName()) || index < 0 || index > order.size()) {
            throw new IllegalArgumentException("Cannot add column " + var.getName() + " at " + index);
        }
        variables.put(var.getName(), var);
        order.add(index, var);
        markAsChanged();
        for (DataStoreListener listener : List.copyOf(dataStoreListeners)) {
            listener.variableAdded(var);
        }
    }

    @Override
    public void moveVariable(final Variable var, final int index) {
        int from = order.indexOf(var);
        if (from < 0 || index < 0 || index >= order.size()) {
            throw new IllegalArgumentException("Cannot move column " + var.getName() + " to " + index);
        }
        if (from == index) {
            return;
        }
        order.remove(from);
        order.add(index, var);
        markAsChanged();
        for (DataStoreListener listener : List.copyOf(dataStoreListeners)) {
            listener.variableOrderChanged();
        }
    }

    @Override
    public void removeCell(final Cell cell) {
        getVariable(cell).removeCell(cell);
        markAsChanged();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String datastoreName) {
        name = datastoreName;
    }

    @Override
    public void markAsUnchanged() {
        if (changed) {
            changed = false;
            if (this.titleNotifier != null) {
                this.titleNotifier.updateTitle();
            }
        }
    }

    @Override
    public void updateVariableName(String oldName, String newName, Variable variable) {
        // A column out of the spreadsheet, as undo leaves one, keeps no name here.
        if (!order.contains(variable)) {
            return;
        }
        this.variables.remove(oldName);
        this.variables.put(newName, variable);
        if (!oldName.equals(newName)) {
            markAsChanged();
            for (DataStoreListener listener : this.dataStoreListeners) {
                listener.variableNameChange(variable);
            }
        }
    }

    @Override
    public boolean isChanged() {
        return changed;
    }

    @Override
    public void setTitleNotifier(final TitleNotifier titleNotifier) {
        this.titleNotifier = titleNotifier;
    }

    @Override
    public void addListener(final DataStoreListener listener) {
        dataStoreListeners.add(listener);
    }

    @Override
    public void removeListener(final DataStoreListener listener) {
        if (dataStoreListeners.contains(listener)) {
            dataStoreListeners.remove(listener);
        }
    }
    
}

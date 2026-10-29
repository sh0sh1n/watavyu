package org.datavyu.controllers.project;

import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.models.db.Variable;
import org.datavyu.projectfile.MediaColumnNames;

import java.util.Set;
import java.util.stream.Collectors;

/** Making media columns: each is one media window, apart from the clips that go in it. */
public final class MediaColumns {

    private MediaColumns() {
    }

    /**
     * Adds an empty media column after the others, named {@code media_#}.
     *
     * @param dataStore the spreadsheet
     * @return the new column, selected as new columns are
     * @throws UserWarningException if the column cannot be added
     */
    public static Variable add(final DataStore dataStore) throws UserWarningException {
        Set<String> names = dataStore.getAllVariables().stream().map(Variable::getName).collect(Collectors.toSet());
        Variable column = dataStore.createMediaVariable(MediaColumnNames.next(names));
        column.setOrderIndex(dataStore.getAllVariables().size() - 1);
        return column;
    }

    /**
     * Adds an empty media column, as Spreadsheet > New Media Column does: it is the one column selected, so media added
     * next goes into it.
     *
     * @param dataStore the spreadsheet
     * @return the new column
     * @throws UserWarningException if the column cannot be added
     */
    public static Variable addSelected(final DataStore dataStore) throws UserWarningException {
        for (Variable column : dataStore.getAllVariables()) {
            column.setSelected(false);
        }
        Variable column = add(dataStore);
        column.setSelected(true);
        return column;
    }

    /**
     * Gives a new spreadsheet the empty media columns it starts with
     * ({@link org.datavyu.util.Settings#getNewSpreadsheetMediaColumns}). Only the first is selected, so media added
     * next goes into it.
     *
     * @param dataStore a new, empty spreadsheet
     * @param count     how many media columns it starts with
     * @throws UserWarningException if a column cannot be added
     */
    public static void startNewSpreadsheet(final DataStore dataStore, final int count) throws UserWarningException {
        for (int i = 0; i < count; i++) {
            add(dataStore).setSelected(i == 0);
        }
        dataStore.markAsUnchanged();
    }
}

package com.sh0sh1n.watavyu.controllers.project;

import com.sh0sh1n.watavyu.models.DataStore;
import com.sh0sh1n.watavyu.models.UserWarningException;
import com.sh0sh1n.watavyu.models.Column;
import com.sh0sh1n.watavyu.projectfile.MediaColumnNames;

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
    public static Column add(final DataStore dataStore) throws UserWarningException {
        Set<String> names = dataStore.getAllColumns().stream().map(Column::getName).collect(Collectors.toSet());
        return dataStore.createMediaColumn(MediaColumnNames.next(names));
    }

    /**
     * Adds an empty media column, as Spreadsheet > New Media Column does: it is the one column selected, so media added
     * next goes into it.
     *
     * @param dataStore the spreadsheet
     * @return the new column
     * @throws UserWarningException if the column cannot be added
     */
    public static Column addSelected(final DataStore dataStore) throws UserWarningException {
        for (Column column : dataStore.getAllColumns()) {
            column.setSelected(false);
        }
        Column column = add(dataStore);
        column.setSelected(true);
        return column;
    }

    /**
     * Gives a new spreadsheet the empty media columns it starts with
     * ({@link com.sh0sh1n.watavyu.util.Settings#getNewSpreadsheetMediaColumns}). Only the first is selected, so media added
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

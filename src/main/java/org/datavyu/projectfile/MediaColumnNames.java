package org.datavyu.projectfile;

import java.util.Set;

/** Names for the media column of newly added media: {@code media_1}, {@code media_2} and so on. */
public final class MediaColumnNames {

    private static final String PREFIX = "media_";

    private MediaColumnNames() {
    }

    /**
     * Makes a name for a media column.
     *
     * @param taken the names already used in the project
     * @return {@code media_} and the smallest number from 1 that makes a name not in {@code taken}
     */
    public static String next(final Set<String> taken) {
        int number = 1;
        while (taken.contains(PREFIX + number)) {
            number++;
        }
        return PREFIX + number;
    }
}

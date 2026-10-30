/**
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
package org.datavyu.util;

import java.io.File;


/**
 * Utilities for file system.
 */
public final class FileSystemUtils {

    private FileSystemUtils() {
    }


    /**
     * Picks the directory a file chooser should start in.
     *
     * @param savedPath The remembered directory; null or empty if none was ever chosen.
     * @param fallback The directory to use when the remembered one is unset or no longer exists.
     *
     * @return The remembered directory if it exists; otherwise the fallback.
     */
    public static File existingDirectoryOr(final String savedPath, final File fallback) {
        if (savedPath == null || savedPath.isEmpty()) {
            return fallback;
        }
        File saved = new File(savedPath);
        return saved.isDirectory() ? saved : fallback;
    }
}

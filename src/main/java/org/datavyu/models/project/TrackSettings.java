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
package org.datavyu.models.project;



/**
 * This class is used to store user interface settings relating to a given track.
 *
 * @author Douglas Teoh
 */
// TODO: Could this functionality be in track model?
public final class TrackSettings {

    /** Absolute file path to the data source */
    @Deprecated private String filePath;

    /** Is the track's movement locked on the interface */
    private boolean isLocked;

    public TrackSettings() {
    }

    /**
     * @return is the track's movement locked on the interface
     */
    public boolean isLocked() {
        return isLocked;
    }

    /**
     * @param isLocked
     *            is track's movement locked on the interface
     */
    public void setLocked(final boolean isLocked) {
        this.isLocked = isLocked;
    }

    /**
     * @return the filePath
     */
    @Deprecated public String getFilePath() {
        return filePath;
    }

    /**
     * @param filePath Set this file path
     */
    @Deprecated public void setFilePath(final String filePath) {
        this.filePath = filePath;
    }
}

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
package org.datavyu.controllers.component;

import org.datavyu.event.component.CarriageEvent;
import org.datavyu.event.component.CarriageEventAdapter;
import org.datavyu.event.component.CarriageEventListener;
import org.datavyu.models.Identifier;
import org.datavyu.models.component.*;
import org.datavyu.plugins.CustomActions;
import org.datavyu.plugins.ViewerStateListener;
import org.datavyu.views.component.TrackPainter;
import org.datavyu.views.component.TracksEditorPainter;

import javax.swing.*;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Manages multiple TrackController instances.
 */
public final class TracksEditorController {

    /** Main UI panel */
    private JPanel editingPanel;

    /** List of track controllers */
    private final Map<Identifier, TrackController> tracks;

    private final MixerModel mixerModel;

    /** Handles the selection model for tracks */
    private final CarriageSelection selectionHandler;

    /**
     * Create a new tracks editor controller
     */
    TracksEditorController(final MixerModel mixerModel) {
        tracks = new LinkedHashMap<>();
        this.mixerModel = mixerModel;
        selectionHandler = new CarriageSelection();
        initView();
    }

    /**
     * Initialise UI elements.
     */
    private void initView() {
        editingPanel = new TracksEditorPainter();
    }

    /**
     * @return Main tracks editor view
     */
    public JComponent getView() {
        return editingPanel;
    }

    public long getMinTime() {
        long minTime = Long.MAX_VALUE;
        for (TrackController trackController : tracks.values()) {
            minTime = Math.min(minTime, trackController.getOffset());
        }
        return minTime;
    }

    public long getMaxTime() {
        long maxTime = Long.MIN_VALUE;
        for (TrackController trackController : tracks.values()) {
            maxTime = Math.max(maxTime, trackController.getOffset() + trackController.getDuration());
        }
        return maxTime;
    }

    /**
     * Adds a new track to the interface
     *
     * @param icon         Icon associated with the track
     * @param trackId      Track identifier
     * @param mediaPath    Path to the media file
     * @param duration     Duration of the track in milliseconds
     * @param offset       Track offset in milliseconds
     * @param listener     Register the listener interested in {@link CarriageEvent}.
     *                     Null if uninterested
     * @param trackPainter The track painter to use
     * @param frameRate    Stream's Frame Rate
     */
    public void addNewTrack(final Identifier trackId, final ImageIcon icon,
                            final File mediaPath, final long duration,
                            final long offset, final CarriageEventListener listener,
                            final TrackPainter trackPainter, final float frameRate) {

        // TrackController
        final TrackController trackController = new TrackController(mixerModel, trackPainter);
        trackController.setTrackInformation(trackId, icon, mediaPath, duration, offset, frameRate);
        if (duration < 0) {
            trackController.setErroneous(true);
        }

        if (listener != null) {
            trackController.addCarriageEventListener(listener);
        }

        trackController.addCarriageEventListener(selectionHandler);
        trackController.attachAsWindowListener();

        tracks.put(trackId, trackController);

        editingPanel.add(trackController.getView(),"pad 0 0 0 " + -TrackConstants.TIMELINE_END_PADDING + ", growx");
        editingPanel.invalidate();

        // Make the newly added track visible.
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                editingPanel.scrollRectToVisible(trackController.getView().getBounds());
            }
        });
    }

    /**
     * Bind track actions to a data viewer.
     *
     * @param trackId Track identifier.
     * @param actions Actions to bind with.
     */
    public void bindTrackActions(final Identifier trackId, final CustomActions actions) {
        TrackController trackController = tracks.get(trackId);
        if (trackController != null) {
            trackController.bindTrackActions(actions);
        }
    }

    public ViewerStateListener getViewerStateListener(final Identifier trackId) {
        return tracks.get(trackId);
    }

    /**
     * Remove a specific track from the controller. Also unregisters the given listener from the track
     *
     * @param trackId  track identifier
     * @param listener listener for carriage events
     * @return true if a track was removed, false otherwise
     */
    public boolean removeTrack(final Identifier trackId, final CarriageEventListener listener) {

        if (tracks.containsKey(trackId)) {
            TrackController trackController = tracks.remove(trackId);

            trackController.removeCarriageEventListener(listener);
            trackController.removeCarriageEventListener(selectionHandler);
            editingPanel.remove(trackController.getView());
            editingPanel.validate();
            editingPanel.repaint();

            return true;

        } else {
            return false;
        }
    }

    /**
     * Remove all tracks from the controller.
     */
    public void removeAllTracks() {

        for (TrackController trackController : tracks.values()) {
            trackController.removeCarriageEventListener(selectionHandler);
        }

        tracks.clear();
        editingPanel.removeAll();
        editingPanel.repaint();
    }

    /**
     * Sets the track offset for the given media if it exists.
     *
     * @param trackId   Identifies a track
     * @param newOffset New track offset (onset) in milliseconds
     * @return true if the track exists, false otherwise
     */
    public boolean setTrackOffset(final Identifier trackId, final long newOffset) {
        TrackController trackController = tracks.get(trackId);
        if (trackController == null) {
            return false;
        }
        trackController.setTrackOffset(newOffset);
        return true;
    }

    /**
     * @return number of tracks being managed by this controller
     */
    public int numberOfTracks() {
        return tracks.size();
    }

    /**
     * Sets the carriage movement locking state.
     *
     * @param lockState true if carriages are not allowed to move, false otherwise.
     */
    public void setLockedState(final boolean lockState) {
        for (TrackController trackController : tracks.values()) {
            trackController.setLocked(lockState);
        }
    }

    /**
     * Set the movement lock state for a given track
     *
     * @param trackId Track identifier
     * @param lock true if the track's movement is locked, false otherwise
     */
    public void setMovementLock(final Identifier trackId, final boolean lock) {
        TrackController trackController = tracks.get(trackId);
        if (trackController != null) {
            trackController.setLocked(lock);
        }
    }

    /**
     * Set the movement lock state for a given track. For backwards compatibility only.
     *
     * @param mediaPath Absolute path to the media file represented by the track
     * @param lock      true if the track's movement is locked, false otherwise
     */
    @Deprecated
    public void setMovementLock(final String mediaPath, final boolean lock) {
        for (TrackController trackController : tracks.values()) {
            if (trackController.getTrackModel().getSourceFile().equals(mediaPath)) {
                trackController.setLocked(lock);
                return;
            }
        }
    }

    /**
     * Get the track model for a given identifier.
     *
     * @param trackId identifier used to search for the track
     * @return null if there is no such track, the associated TrackModel otherwise
     */
    public TrackModel getTrackModel(final Identifier trackId) {
        TrackController trackController = tracks.get(trackId);
        return (trackController != null) ? trackController.getTrackModel() : null;
    }

    public boolean isAnyTrackUnlocked() {
        for (TrackController trackController : tracks.values()) {
            if (!trackController.getTrackModel().isLocked()) {
                return true;
            }
        }
        return tracks.isEmpty();
    }

    /**
     * Deselect all tracks except for the given track.
     *
     * @param selected
     */
    private void deselectExcept(final TrackController selected) {
        for (TrackController trackController : tracks.values()) {
            if (trackController != selected) {
                trackController.deselect();
            }
        }
    }

    /**
     * Inner class for handling carriage selection.
     */
    private class CarriageSelection extends CarriageEventAdapter {

        @Override
        public void selectionChanged(final CarriageEvent e) {
            if (!e.hasModifiers()) {
                deselectExcept((TrackController) e.getSource());
            }
        }
    }
}

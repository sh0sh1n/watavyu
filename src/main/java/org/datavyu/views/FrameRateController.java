package org.datavyu.views;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintains the highest frame rate between the tracks and one user defined entry
 *
 * The user can always overwrite the highest frame rate
 */
public class FrameRateController {

    private static final long USER_FRAME_RATE_ID = -1;

    private Map<Long, Float> trackToFrameRate = new HashMap<>();

    public synchronized void addFrameRate(long id, float frameRate) {
        trackToFrameRate.put(id, frameRate);
    }

    public synchronized void removeFrameRate(long id) {
        trackToFrameRate.remove(id);
    }

    private float getHighestFrameRate() {
        float highestFrameRate = 0F;
        for (float frameRate : trackToFrameRate.values()) {
            highestFrameRate = Math.max(highestFrameRate, frameRate);
        }
        return highestFrameRate;
    }

    public synchronized float getFrameRate() {
        return getHighestFrameRate();
    }

}

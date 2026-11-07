package org.datavyu.views;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains the highest frame rate between the tracks and one user defined entry
 *
 * The user can always overwrite the highest frame rate
 */
public class FrameRateController {


    private final Map<Long, Float> trackToFrameRate = new ConcurrentHashMap<>();

    public void addFrameRate(long id, float frameRate) {
        trackToFrameRate.put(id, frameRate);
    }

    public void removeFrameRate(long id) {
        trackToFrameRate.remove(id);
    }

    private float getHighestFrameRate() {
        float highestFrameRate = 0F;
        for (float frameRate : trackToFrameRate.values()) {
            highestFrameRate = Math.max(highestFrameRate, frameRate);
        }
        return highestFrameRate;
    }

    public float getFrameRate() {
        return getHighestFrameRate();
    }

}

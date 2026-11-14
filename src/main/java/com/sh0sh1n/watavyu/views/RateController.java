package com.sh0sh1n.watavyu.views;


import java.util.*;

/**
 * Class to control rates.
 */
final class RateController {

    private NavigableSet<Float> rates;

    /**
     * Rate controller for the rates.
     *
     * @param rates The rates for this controller.
     */
    private RateController(float[] rates) {
        this.rates = new TreeSet<>();
        for (float rate : rates) {
            this.rates.add(rate);
        }
    }

    /**
     * The shuttle's rates: 0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3 and 4, all forward. Beyond 4x, people jump or scrub
     * instead. There is no rate 0, as playback that should not move is paused, and no reverse.
     */
    RateController() {
        this(new float[]{0.25F, 0.5F, 0.75F, 1F, 1.25F, 1.5F, 2F, 3F, 4F});
    }

    /** Returns the rates faster than a rate, slowest first. */
    private List<Float> largerRates(float rate) {
        return new ArrayList<>(rates.tailSet(rate, false));
    }

    /** Returns the rates slower than a rate, fastest first. */
    private List<Float> smallerRates(float rate) {
        return new ArrayList<>(rates.headSet(rate, false).descendingSet());
    }

    /**
     * Get the next rate for the current rate, going no faster than the fastest rate or slower than the slowest.
     * @param currentRate The current rate.
     * @param jump The number of jumps to the next currentRate.
     *
     * @return Next rate for the number of signed jumps taking the current rate as a reference.
     */
    float nextRate(float currentRate, int jump) {
        List<Float> rates = jump < 0 ? smallerRates(currentRate) : largerRates(currentRate);
        float nextRate = currentRate;
        Iterator<Float> rate = rates.iterator();
        for (int jumps = 0; rate.hasNext() && jumps < Math.abs(jump); ++jumps) {
            nextRate = rate.next();
        }
        return nextRate;
    }
}

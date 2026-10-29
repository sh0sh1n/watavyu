package org.datavyu.views;


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
     * The shuttle's rates: 0, then 0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 3 and 4 in each direction. Beyond 4x, people
     * jump or scrub instead.
     */
    RateController() {
        this(new float[]{-4F, -3F, -2F, -1.5F, -1.25F, -1F, -0.75F, -0.5F, -0.25F,
                           0F, 0.25F, 0.5F, 0.75F, 1F, 1.25F, 1.5F, 2F, 3F, 4F});
    }

    /**
     * Get the next larger rate. If this is the largest rate, it returns this largest rate.
     *
     * @param rate The current rate.
     *
     * @return Next larger rate.
     */
    private List<Float> largerRates(float rate) {
        SortedSet<Float> tailSet = rates.tailSet(rate);
        List<Float> larger = new ArrayList<>(tailSet);
        larger.remove(0);
        return larger;
    }

    /**
     * Get the next smaller rate. If this is the smallest rate, it returns this smallest rate.
     *
     * @param rate The current rate.
     *
     * @return Next smaller rate.
     */
    private List<Float> smallerRates(float rate) {
        return new ArrayList<>(rates.headSet(rate, false).descendingSet());
    }

    /**
     * Get the next rate for the current rate.
     * @param currentRate The current rate.
     * @param jump The number of jumps to the next currentRate.
     *
     * @return Next rate for the number of signed jumps taking the current rate as a reference.
     */
    float nextRate(float currentRate, int jump) {
        return nextRate(currentRate, jump, true);
    }

    /**
     * Get the next rate for the current rate.
     * @param currentRate The current rate.
     * @param jump The number of jumps to the next currentRate.
     * @param reverseAllowed False to stop at zero instead of moving to a negative rate.
     *
     * @return Next rate for the number of signed jumps taking the current rate as a reference.
     */
    float nextRate(float currentRate, int jump, boolean reverseAllowed) {
        List<Float> rates = jump < 0 ? smallerRates(currentRate) : largerRates(currentRate);
        float nextRate = currentRate;
        Iterator<Float> rate = rates.iterator();
        for (int jumps = 0; rate.hasNext() && jumps < Math.abs(jump); ++jumps) {
            nextRate = rate.next();
        }
        return reverseAllowed ? nextRate : Math.max(nextRate, 0F);
    }
}

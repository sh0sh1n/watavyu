package com.sh0sh1n.watavyu.views;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class RateControllerTest {

    private final RateController rates = new RateController();

    @Test
    public void shuttlesThroughTheRateLadder() {
        float[] ladder = {0.25F, 0.5F, 0.75F, 1F, 1.25F, 1.5F, 2F, 3F, 4F};
        for (int i = 1; i < ladder.length; i++) {
            assertEquals(rates.nextRate(ladder[i - 1], 1), ladder[i]);
            assertEquals(rates.nextRate(ladder[i], -1), ladder[i - 1]);
        }
    }

    /** There is no rate 0 and no reverse: shuttling goes no slower than a quarter and no faster than 4×. */
    @Test
    public void staysBetweenAQuarterAndFour() {
        assertEquals(rates.nextRate(0.25F, -1), 0.25F);
        assertEquals(rates.nextRate(4F, 1), 4F);
    }

    @Test
    public void jumpsSeveralRatesAtOnce() {
        assertEquals(rates.nextRate(0.25F, 3), 1F);
        assertEquals(rates.nextRate(1F, -20), 0.25F);
    }

    /** A rate between two of the ladder's goes to the next one either way. */
    @Test
    public void aRateOffTheLadderGoesToTheNextOnIt() {
        assertEquals(rates.nextRate(1.1F, 1), 1.25F);
        assertEquals(rates.nextRate(1.1F, -1), 1F);
    }
}

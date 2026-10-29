package org.datavyu.views;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class RateControllerTest {

    private final RateController rates = new RateController();

    @Test
    public void shuttlesThroughTheRateLadder() {
        float[] ladder = {0F, 0.25F, 0.5F, 0.75F, 1F, 1.25F, 1.5F, 2F, 3F, 4F};
        for (int i = 1; i < ladder.length; i++) {
            assertEquals(rates.nextRate(ladder[i - 1], 1), ladder[i]);
            assertEquals(rates.nextRate(ladder[i], -1), ladder[i - 1]);
        }
        assertEquals(rates.nextRate(4F, 1), 4F, "the fastest rate is the limit");
        assertEquals(rates.nextRate(0F, -1), -0.25F);
        assertEquals(rates.nextRate(-4F, -1), -4F, "the fastest reverse rate is the limit");
    }

    @Test
    public void jumpsSeveralRatesAtOnce() {
        assertEquals(rates.nextRate(0F, 4), 1F);
        assertEquals(rates.nextRate(1F, -20), -4F);
    }

    @Test
    public void stopsAtZeroWhenReverseIsNotAllowed() {
        assertEquals(rates.nextRate(0.25F, -1, false), 0F);
        assertEquals(rates.nextRate(0F, -1, false), 0F);
        assertEquals(rates.nextRate(0F, -3, false), 0F);
        assertEquals(rates.nextRate(1F, -1, false), 0.75F, "forward rates are unaffected");
        assertEquals(rates.nextRate(0F, 1, false), 0.25F);
    }
}

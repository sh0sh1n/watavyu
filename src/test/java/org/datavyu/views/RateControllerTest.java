package org.datavyu.views;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class RateControllerTest {

    private final RateController rates = new RateController();

    @Test
    public void shuttlesThroughTheRateLadder() {
        assertEquals(rates.nextRate(1F, 1), 2F);
        assertEquals(rates.nextRate(1F, -1), 0.5F);
        assertEquals(rates.nextRate(0F, -1), -1 / 32F);
        assertEquals(rates.nextRate(32F, 1), 32F, "the fastest rate is the limit");
    }

    @Test
    public void stopsAtZeroWhenReverseIsNotAllowed() {
        assertEquals(rates.nextRate(1 / 32F, -1, false), 0F);
        assertEquals(rates.nextRate(0F, -1, false), 0F);
        assertEquals(rates.nextRate(0F, -3, false), 0F);
        assertEquals(rates.nextRate(1F, -1, false), 0.5F, "forward rates are unaffected");
        assertEquals(rates.nextRate(0F, 1, false), 1 / 32F);
    }
}

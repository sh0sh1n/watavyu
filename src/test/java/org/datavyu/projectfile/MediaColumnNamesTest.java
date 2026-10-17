package org.datavyu.projectfile;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import java.util.Set;

public class MediaColumnNamesTest {

    @Test
    public void theFirstMediaColumnIsMedia1() {
        assertEquals(MediaColumnNames.next(Set.of("test")), "media_1");
    }

    @Test
    public void aTakenNumberIsSkipped() {
        assertEquals(MediaColumnNames.next(Set.of("media_1", "media_3")), "media_2");
    }
}

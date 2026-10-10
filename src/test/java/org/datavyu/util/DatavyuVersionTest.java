package org.datavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.testng.annotations.Test;

public class DatavyuVersionTest {

    @Test
    public void versionIsTheGradleProjectVersion() {
        String expected = System.getProperty("expectedVersion");
        assertNotNull(expected, "the Gradle test task supplies expectedVersion");
        assertEquals(DatavyuVersion.getVersion(), expected);
    }
}

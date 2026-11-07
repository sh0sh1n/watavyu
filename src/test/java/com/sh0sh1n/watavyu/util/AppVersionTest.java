package com.sh0sh1n.watavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.testng.annotations.Test;

public class AppVersionTest {

    @Test
    public void versionIsTheGradleProjectVersion() {
        String expected = System.getProperty("expectedVersion");
        assertNotNull(expected, "the Gradle test task supplies expectedVersion");
        assertEquals(AppVersion.getVersion(), expected);
    }
}

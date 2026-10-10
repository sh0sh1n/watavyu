package org.datavyu.util;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class MacOSTest {

    @Test
    public void parsesModernMacOSVersion() {
        assertVersion("27.0", 27);
    }

    @Test
    public void parsesLegacyMacOSVersion() {
        assertVersion("10.15.7", 15);
    }

    private void assertVersion(String osVersion, int expectedVersion) {
        String previousVersion = System.getProperty("os.version");
        try {
            System.setProperty("os.version", osVersion);
            assertEquals(MacOS.getOSVersion(), expectedVersion);
        } finally {
            if (previousVersion == null) {
                System.clearProperty("os.version");
            } else {
                System.setProperty("os.version", previousVersion);
            }
        }
    }
}
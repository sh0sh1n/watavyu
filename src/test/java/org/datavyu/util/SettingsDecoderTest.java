package org.datavyu.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.testng.annotations.Test;

public class SettingsDecoderTest {

    /** A bean standing in for ConfigProperties, whose static initializer needs the running application. */
    public static class Bean {
        private String favoritesFolder;
        private boolean doWarn;

        public String getFavoritesFolder() { return favoritesFolder; }
        public void setFavoritesFolder(String favoritesFolder) { this.favoritesFolder = favoritesFolder; }
        public boolean getDoWarn() { return doWarn; }
        public void setDoWarn(boolean doWarn) { this.doWarn = doWarn; }
    }

    private static Object decode(String body) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<java class=\"java.beans.XMLDecoder\">\n"
            + " <object class=\"" + Bean.class.getName() + "\">\n" + body + " </object>\n</java>\n";
        return SettingsDecoder.decode(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void readsKnownProperties() {
        Bean bean = (Bean) decode(
            "  <void property=\"favoritesFolder\"><string>favorites</string></void>\n"
            + "  <void property=\"doWarn\"><boolean>true</boolean></void>\n");
        assertEquals(bean.getFavoritesFolder(), "favorites");
        assertTrue(bean.getDoWarn());
    }

    /** Settings files written before the update check and telemetry were removed still carry their properties. */
    @Test
    public void skipsRemovedPropertiesAndKeepsTheRest() {
        Bean bean = (Bean) decode(
            "  <void property=\"ignoreVersion\"><string></string></void>\n"
            + "  <void property=\"favoritesFolder\"><string>favorites</string></void>\n"
            + "  <void property=\"shareData\"><boolean>true</boolean></void>\n"
            + "  <void property=\"doWarn\"><boolean>true</boolean></void>\n");
        assertEquals(bean.getFavoritesFolder(), "favorites");
        assertTrue(bean.getDoWarn());
    }
}

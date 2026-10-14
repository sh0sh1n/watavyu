package org.datavyu.util;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class StringUtilsTest {

    @Test
    public void escapeHtmlReplacesMarkupCharacters() {
        assertEquals(StringUtils.escapeHtml("<b>Tom & \"Jerry's\"</b>"),
            "&lt;b&gt;Tom &amp; &quot;Jerry&#39;s&quot;&lt;/b&gt;");
    }

    @Test
    public void escapeHtmlLeavesOtherTextAlone() {
        assertEquals(StringUtils.escapeHtml("code_1 (öäü) 日本"), "code_1 (öäü) 日本");
    }
}

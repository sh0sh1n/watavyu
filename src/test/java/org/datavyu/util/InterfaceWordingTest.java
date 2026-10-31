package org.datavyu.util;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * The interface calls a spreadsheet column a column. Datavyu's names for it, variable and vocab element, and vocab
 * for its columns and codes, stay out of every string the resource files hold.
 */
public class InterfaceWordingTest {

    private static final List<String> OLD_WORDS = List.of("variable", "vocab");

    @Test
    public void noResourceStringUsesDatavyusNamesForAColumn() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".properties")).toList()) {
                Properties strings = new Properties();
                try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    strings.load(in);
                }
                for (String key : strings.stringPropertyNames()) {
                    String text = strings.getProperty(key).toLowerCase(Locale.ROOT);
                    if (OLD_WORDS.stream().anyMatch(text::contains)) {
                        found.add(file.getFileName() + ": " + key + "=" + strings.getProperty(key));
                    }
                }
            }
        }
        assertEquals(found, List.of());
    }
}

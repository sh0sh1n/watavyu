package com.sh0sh1n.watavyu.util;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Each class's interface text is in {@code resources/<ClassName>.properties} beside the class ({@link Resources}), and a
 * file that names no class is never read. Names are compared exactly: the Mac's disk ignores case, so a file named
 * {@code SpreadsheetPanel} is found for {@code SpreadSheetPanel} here but not once the app is packed in a jar.
 */
public class ResourceFilesTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path SOURCES = Path.of("src/main/java");

    /** The application's own text, read for every class, and the version, which is not text. */
    private static final List<String> NOT_A_CLASS =
        List.of("com/sh0sh1n/watavyu/resources/Watavyu.properties", "com/sh0sh1n/watavyu/version.properties");

    @Test
    public void everyPropertiesFileBelongsToAClassOfItsExactName() throws IOException {
        List<String> orphans = new ArrayList<>();
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".properties")).toList()) {
                String relative = RESOURCES.relativize(file).toString();
                if (NOT_A_CLASS.contains(relative)) {
                    continue;
                }
                Path folder = RESOURCES.relativize(file.getParent());
                String name = file.getFileName().toString().replace(".properties", ".java");
                if (!folder.getFileName().toString().equals("resources")
                        || !hasExactly(SOURCES.resolve(folder.getParent()), name)) {
                    orphans.add(relative);
                }
            }
        }
        assertEquals(orphans, List.of());
    }

    /** Whether the folder holds a file of exactly this name, case included. */
    private static boolean hasExactly(final Path folder, final String name) throws IOException {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.anyMatch(f -> f.getFileName().toString().equals(name));
        }
    }
}

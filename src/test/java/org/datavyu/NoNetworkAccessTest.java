package org.datavyu;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.testng.annotations.Test;

/**
 * The application makes no network connections of its own: no update checks, no telemetry.
 *
 * Scans the compiled main classes for references to networking APIs. Class and method names are stored as plain
 * strings in the constant pool, so any use of these types shows up in the class file bytes. Opening a page in the
 * user's browser (Desktop.browse) and reading local resources through java.net.URL are allowed. Third-party
 * libraries and user scripts are not covered.
 */
public class NoNetworkAccessTest {

    private static final String[] FORBIDDEN = {
        "java/net/URLConnection",
        "java/net/HttpURLConnection",
        "java/net/Socket",
        "java/net/ServerSocket",
        "java/net/DatagramSocket",
        "java/net/MulticastSocket",
        "java/net/http/",
        "javax/net/",
        "com/brsanthu/",
    };

    @Test
    public void mainClassesReferenceNoNetworkingApis() throws Exception {
        Path mainClasses = Paths.get(Datavyu.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(mainClasses), "expected compiled classes directory, got " + mainClasses);

        List<String> violations = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(mainClasses)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                scanned++;
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                for (String name : FORBIDDEN) {
                    if (bytes.contains(name)) {
                        violations.add(mainClasses.relativize(file) + " references " + name);
                    }
                }
            }
        } catch (IOException e) {
            throw new AssertionError("Cannot scan " + mainClasses, e);
        }

        assertTrue(scanned > 100, "scanned only " + scanned + " classes in " + mainClasses);
        assertEquals(violations, new ArrayList<String>());
    }
}

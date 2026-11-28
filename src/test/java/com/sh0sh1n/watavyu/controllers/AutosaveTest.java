package com.sh0sh1n.watavyu.controllers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.sh0sh1n.watavyu.projectfile.Fingerprint;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * An autosave belongs to the Watavyu writing it while that Watavyu runs, and is left for the next launch to recover
 * once it stops without saving or discarding the work, however it stops (B11).
 */
public class AutosaveTest {

    private static final String PROJECT = "{\"format\": \"watavyu\", \"version\": 1, \"columns\": []}\n";

    private Path folder;

    /** Autosaves a test made, deleted afterwards so none is left held */
    private final List<Autosave> made = new ArrayList<>();

    @BeforeMethod
    public void setUp() throws IOException {
        folder = Files.createTempDirectory("autosaves");
    }

    @AfterMethod
    public void tearDown() {
        made.forEach(Autosave::delete);
        made.clear();
    }

    private Autosave written(final String project, final Autosave.Origin origin) throws IOException {
        Autosave autosave = Autosave.create(folder);
        made.add(autosave);
        autosave.write(project, origin);
        return autosave;
    }

    private List<Autosave> claim() {
        List<Autosave> claimed = Autosave.claimAbandoned(folder);
        made.addAll(claimed);
        return claimed;
    }

    private List<String> files() throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    public void anAutosaveItsWatavyuStillHoldsIsLeftToIt() throws IOException {
        Autosave autosave = written(PROJECT, null);

        assertTrue(claim().isEmpty());
        assertTrue(Files.exists(autosave.getFile()));
    }

    @Test
    public void anAutosaveLetGoIsClaimedWithItsWorkAndWhereItCameFrom() throws IOException {
        Autosave.Origin origin = new Autosave.Origin(new File("/Users/coder/study.watavyu.json"), "abc123");
        written(PROJECT, origin).release();

        List<Autosave> claimed = claim();

        assertEquals(claimed.size(), 1);
        assertEquals(claimed.get(0).getOrigin(), origin);
        assertEquals(claimed.get(0).getContents(), Fingerprint.of(PROJECT));
        assertEquals(Files.readString(claimed.get(0).getFile()), PROJECT);
    }

    @Test
    public void untitledWorkHasNoOrigin() throws IOException {
        written(PROJECT, null).release();

        assertNull(claim().get(0).getOrigin());
    }

    @Test
    public void aClaimedAutosaveIsOwnedByTheWatavyuThatClaimedIt() throws IOException {
        written(PROJECT, null).release();

        assertEquals(claim().size(), 1);
        assertTrue(claim().isEmpty());
    }

    @Test
    public void anAutosaveNeverWrittenHoldsNothingAndIsCleared() throws IOException {
        Autosave autosave = Autosave.create(folder);
        autosave.release();

        assertTrue(claim().isEmpty());
        assertEquals(files(), List.of());
    }

    @Test
    public void anAutosaveIsOneProjectFileAndItsLockReplacedInPlace() throws IOException {
        Autosave autosave = written(PROJECT, null);
        Path file = autosave.getFile();
        String later = PROJECT.replace("[]", "[ ]");

        autosave.write(later, null);

        assertEquals(autosave.getFile(), file);
        assertEquals(Files.readString(file), later);
        assertEquals(autosave.getContents(), Fingerprint.of(later));
        assertEquals(files().size(), 2, files().toString());
    }

    @Test
    public void deletingLeavesNothing() throws IOException {
        Autosave autosave = written(PROJECT, null);

        autosave.delete();

        assertEquals(files(), List.of());
    }

    @Test
    public void releasingLeavesTheAutosaveOnDisk() throws IOException {
        Autosave autosave = written(PROJECT, null);

        autosave.release();

        assertTrue(Files.exists(autosave.getFile()));
        assertEquals(files().size(), 2, files().toString());
    }

    @Test
    public void aMissingFolderHoldsNothingToClaim() {
        assertTrue(Autosave.claimAbandoned(folder.resolve("missing")).isEmpty());
    }

    /**
     * The operating system lets go of a lock when its process dies, so an autosave whose Watavyu was killed, which
     * runs no shutdown code, is claimed; while that Watavyu ran, it was not.
     */
    @Test
    public void aKilledWatavyuLeavesItsAutosaveToRecover() throws Exception {
        Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
        Process holder = new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
            AutosaveHolder.class.getName(), folder.toString())
            .redirectErrorStream(true)
            .start();
        try {
            BufferedReader output = new BufferedReader(new InputStreamReader(holder.getInputStream(),
                StandardCharsets.UTF_8));
            String line;
            do {
                line = output.readLine();
            } while (line != null && !line.equals(AutosaveHolder.READY));
            assertEquals(line, AutosaveHolder.READY, "the holder wrote its autosave");

            assertTrue(claim().isEmpty(), "a running Watavyu's autosave is its own");

            holder.destroyForcibly();
            assertTrue(holder.waitFor(10, TimeUnit.SECONDS));

            List<Autosave> claimed = claim();
            assertEquals(claimed.size(), 1);
            assertEquals(Files.readString(claimed.get(0).getFile()), AutosaveHolder.PROJECT);
        } finally {
            holder.destroyForcibly();
        }
    }
}

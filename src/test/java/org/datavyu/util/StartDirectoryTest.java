package org.datavyu.util;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.testng.annotations.Test;

/** File choosers open in the last chosen directory, or the user's home when there is none to use. */
public class StartDirectoryTest {

    private static final File HOME = new File("/fallback/home");

    @Test
    public void usesTheSavedDirectoryWhenItExists() throws IOException {
        File saved = Files.createTempDirectory("start-dir").toFile();
        try {
            assertEquals(FileSystemUtils.existingDirectoryOr(saved.getAbsolutePath(), HOME), saved);
        } finally {
            saved.delete();
        }
    }

    @Test
    public void fallsBackWhenNothingWasSaved() {
        assertEquals(FileSystemUtils.existingDirectoryOr(null, HOME), HOME);
        assertEquals(FileSystemUtils.existingDirectoryOr("", HOME), HOME);
    }

    /** For example a settings file carried over from another machine, or a folder deleted since. */
    @Test
    public void fallsBackWhenTheSavedDirectoryIsGone() {
        assertEquals(FileSystemUtils.existingDirectoryOr("C:\\Users\\Florian", HOME), HOME);
    }

    @Test
    public void fallsBackWhenTheSavedPathIsAFile() throws IOException {
        File file = File.createTempFile("start-dir", ".txt");
        try {
            assertEquals(FileSystemUtils.existingDirectoryOr(file.getAbsolutePath(), HOME), HOME);
        } finally {
            file.delete();
        }
    }
}

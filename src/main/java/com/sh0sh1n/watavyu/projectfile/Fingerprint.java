package com.sh0sh1n.watavyu.projectfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A file's contents in short: the SHA-256 digest of its bytes, in hexadecimal. Two files with the same fingerprint
 * hold the same bytes, so it tells whether a project file changed since it was read or written, whatever its
 * modification time says.
 */
public final class Fingerprint {

    private Fingerprint() {
    }

    /**
     * Returns the fingerprint of a file's contents.
     *
     * @param bytes a file's contents
     * @return their fingerprint
     */
    public static String of(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }

    /**
     * Returns the fingerprint of a file holding text, written in UTF-8 as project files are.
     *
     * @param text the file's contents
     * @return the fingerprint of the file
     */
    public static String of(final String text) {
        return of(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the fingerprint of a file on disk.
     *
     * @param file a file
     * @return the fingerprint of its contents, or null if there is no such file
     * @throws IOException if it exists but cannot be read
     */
    public static String ofFile(final Path file) throws IOException {
        try {
            return of(Files.readAllBytes(file));
        } catch (NoSuchFileException e) {
            return null;
        }
    }
}

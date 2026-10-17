package org.datavyu.projectfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Reads and writes {@code .watavyu.json} project files.
 *
 * <p>Reading is strict: an unknown or missing key, a value of the wrong type or a broken rule is an error naming the
 * place in the file, so a hand edit that the app would misread is refused rather than guessed at. Writing checks the
 * same rules first, so the app never writes a file it would refuse to read.
 *
 * <p>The layout is fixed ({@link ProjectFileWriter}), so a file written by Watavyu is rewritten byte for byte when
 * nothing changed.
 */
public final class ProjectJson {

    /** The permissions of a project file saved for the first time, as with the usual umask of 022. */
    private static final Set<PosixFilePermission> NEW_FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-r--r--");

    private ProjectJson() {
    }

    /**
     * Reads a project file.
     *
     * @param file a project file
     * @return the project it holds
     * @throws ProjectFileException if the file cannot be read or breaks the format
     */
    public static ProjectDocument read(final Path file) throws ProjectFileException {
        try (InputStream in = Files.newInputStream(file)) {
            return read(in);
        } catch (IOException e) {
            throw new ProjectFileException("Unable to read " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads a project file's contents.
     *
     * @param in a project file's contents, UTF-8
     * @return the project it holds
     * @throws ProjectFileException if the contents break the format
     * @throws IOException          if they cannot be read
     */
    public static ProjectDocument read(final InputStream in) throws ProjectFileException, IOException {
        ProjectDocument document = ProjectFileReader.read(in);
        ProjectRules.check(document);
        return document;
    }

    /**
     * Writes a project file, replacing any file already there only once the new one is complete. The file keeps its
     * permissions, and a symbolic link to it stays a link.
     *
     * @param file     where to write
     * @param document the project
     * @throws ProjectFileException if the project breaks the format or the file cannot be written
     */
    public static void write(final Path file, final ProjectDocument document) throws ProjectFileException {
        String text = toJson(document);
        Path temporary = null;
        try {
            // A symbolic link stays a link: the file it points to is the one replaced.
            Path target = Files.isSymbolicLink(file) ? file.toRealPath() : file.toAbsolutePath();
            temporary = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".tmp");
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(temporary, Files.exists(target)
                ? Files.getPosixFilePermissions(target) : NEW_FILE_PERMISSIONS);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new ProjectFileException("Unable to save " + file.getFileName() + ": " + e.getMessage(), e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The move succeeded or the write failed; either way there is nothing more to do.
                }
            }
        }
    }

    /**
     * Formats a project as a project file's contents.
     *
     * @param document a project
     * @return its project file's contents
     * @throws ProjectFileException if the project breaks the format
     */
    public static String toJson(final ProjectDocument document) throws ProjectFileException {
        ProjectRules.check(document);
        return ProjectFileWriter.toJson(document);
    }
}

package org.datavyu.projectfile;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * How a project file names its media files: relative to the folder holding the project file when the file is inside
 * it, absolute otherwise, always with {@code /} separators.
 */
public final class ProjectPaths {

    private ProjectPaths() {
    }

    /**
     * Names a media file as a project file stores it.
     *
     * @param projectFolder the folder holding the project file
     * @param media         a media file
     * @return how the project file names the media file
     */
    public static String stored(final Path projectFolder, final Path media) {
        Path folder = projectFolder.toAbsolutePath().normalize();
        Path file = media.toAbsolutePath().normalize();
        return file.startsWith(folder) ? folder.relativize(file).toString() : file.toString();
    }

    /**
     * Finds the media file a project file names.
     *
     * @param projectFolder the folder holding the project file
     * @param stored        how the project file names a media file
     * @return the media file
     */
    public static Path resolve(final Path projectFolder, final String stored) {
        Path path = Paths.get(stored);
        return path.isAbsolute() ? path.normalize() : projectFolder.toAbsolutePath().resolve(path).normalize();
    }

    /**
     * Names a project after its file.
     *
     * @param projectFile a project file
     * @return the project's name: the file name without {@value ProjectDocument#EXTENSION}
     */
    public static String projectName(final Path projectFile) {
        String name = projectFile.getFileName().toString();
        return name.endsWith(ProjectDocument.EXTENSION)
            ? name.substring(0, name.length() - ProjectDocument.EXTENSION.length()) : name;
    }
}

package org.datavyu.projectfile;

import org.datavyu.projectfile.ProjectDocument.Clip;
import org.datavyu.projectfile.ProjectDocument.MediaColumn;
import org.datavyu.projectfile.ProjectDocument.Source;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

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
        return isProjectFile(name) ? name.substring(0, name.length() - ProjectDocument.EXTENSION.length()) : name;
    }

    /**
     * Tells whether a file name is a project file's, in any letter case, as macOS file names match.
     *
     * @param fileName a file name
     * @return whether it ends with {@value ProjectDocument#EXTENSION}
     */
    public static boolean isProjectFile(final String fileName) {
        int start = fileName.length() - ProjectDocument.EXTENSION.length();
        return start >= 0 && fileName.regionMatches(true, start, ProjectDocument.EXTENSION, 0,
            ProjectDocument.EXTENSION.length());
    }

    /**
     * Makes a media column's file paths absolute, for holding it while the project is open.
     *
     * @param column        a media column as a project file stores it
     * @param projectFolder the folder holding the project file it came from
     * @return the column with absolute paths
     */
    public static MediaColumn absolutePaths(final MediaColumn column, final Path projectFolder) {
        return withPaths(column, file -> resolve(projectFolder, file).toString());
    }

    /**
     * Names a media column's files as a project file in a folder stores them.
     *
     * @param column        a media column with absolute paths
     * @param projectFolder the folder the project file is saved in
     * @return the column with stored paths
     */
    public static MediaColumn storedPaths(final MediaColumn column, final Path projectFolder) {
        return withPaths(column, file -> stored(projectFolder, Paths.get(file)));
    }

    private static MediaColumn withPaths(final MediaColumn column, final UnaryOperator<String> path) {
        List<Clip> clips = new ArrayList<>();
        for (Clip clip : column.clips()) {
            Source source = clip.source();
            clips.add(new Clip(clip.onset(), clip.volume(), new Source(path.apply(source.file()), source.duration(),
                source.frameRate(), source.width(), source.height())));
        }
        return new MediaColumn(column.name(), column.hidden(), column.locked(), clips);
    }
}

package com.sh0sh1n.watavyu.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sh0sh1n.watavyu.projectfile.Fingerprint;
import com.sh0sh1n.watavyu.projectfile.ProjectDocument;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One autosave on disk: a project file holding one spreadsheet's unsaved work, {@code <id>.watavyu.json}, and beside
 * it a lock file, {@code <id>.lock}, that the Watavyu writing the autosave keeps locked while it runs. The operating
 * system lets go of the lock when that Watavyu stops, however it stops, so an autosave whose lock can be taken is one
 * no running Watavyu owns: its work was neither saved nor discarded, and it can be recovered.
 *
 * <p>The lock file also says where the work came from ({@link Origin}), so recovered work saves back to its project
 * file, and the spreadsheet's name, so work never saved keeps it. The lock file is in place before the project file is first written and goes after it, and the project file is
 * replaced only once its new contents are complete and on disk, so a crash at any moment leaves the last autosave whole.
 */
public final class Autosave {

    /** Logger for this class */
    private static final Logger logger = LogManager.getLogger(Autosave.class);

    /** The lock file's extension */
    private static final String LOCK = ".lock";

    /** The extension of a lock file not yet locked and in place; no other Watavyu looks at it */
    private static final String NEW_LOCK = LOCK + ".new";

    /** The extension of a project file being written, before it replaces the autosave */
    private static final String PARTIAL = ".partial";

    /** How old a lock file not yet in place must be before another Watavyu takes it as left behind */
    static final long STALE_MILLIS = 60_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The autosaves this Watavyu owns, by id. Closing any channel on a file lets go of every lock the process holds on
     * it, so this Watavyu never opens the lock file of an autosave it owns.
     */
    private static final Set<String> OWNED = ConcurrentHashMap.newKeySet();

    /**
     * Where an autosave's work came from.
     *
     * @param projectFile the project file the spreadsheet was opened from or last saved to
     * @param fingerprint the project file's fingerprint as the spreadsheet last read or wrote it, or null if unknown
     */
    public record Origin(File projectFile, String fingerprint) {

        public Origin {
            Objects.requireNonNull(projectFile, "projectFile");
        }
    }

    private final Path folder;

    private final String id;

    private final FileChannel channel;

    private final FileLock lock;

    /** Where the work came from, as the lock file says; null for an untitled spreadsheet */
    private Origin origin;

    /** The spreadsheet's name, as the lock file says; null for one that has none */
    private String projectName;

    /** The fingerprint of the project file last written, or null before the first */
    private String contents;

    private Autosave(final Path folder, final String id, final FileChannel channel, final FileLock lock) {
        this.folder = folder;
        this.id = id;
        this.channel = channel;
        this.lock = lock;
        OWNED.add(id);
    }

    /**
     * Starts a new autosave, owned by this Watavyu until it is deleted or released. Nothing is saved until
     * {@link #write}.
     *
     * @param folder the folder autosaves are kept in, made if it is missing
     * @return the autosave
     * @throws IOException if its lock file cannot be made and locked
     */
    public static Autosave create(final Path folder) throws IOException {
        Files.createDirectories(folder);
        String id = UUID.randomUUID().toString();
        OWNED.add(id);
        // Locked under a name no other Watavyu reads, then moved into place, so none sees it unlocked and takes it.
        Path newLock = folder.resolve(id + NEW_LOCK);
        FileChannel channel;
        try {
            channel = FileChannel.open(newLock, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ,
                StandardOpenOption.WRITE);
        } catch (IOException e) {
            OWNED.remove(id);
            throw e;
        }
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new IOException("Could not lock the new autosave " + newLock);
            }
            Files.move(newLock, folder.resolve(id + LOCK), StandardCopyOption.ATOMIC_MOVE);
            return new Autosave(folder, id, channel, lock);
        } catch (IOException | RuntimeException e) {
            OWNED.remove(id);
            channel.close();
            Files.deleteIfExists(newLock);
            throw e;
        }
    }

    /**
     * Takes every autosave in a folder that no running Watavyu owns, so this one owns them. An autosave whose project
     * file was never written is deleted, as there is nothing in it to recover.
     *
     * @param folder the folder autosaves are kept in; it need not exist
     * @return the autosaves taken, oldest first
     */
    public static List<Autosave> claimAbandoned(final Path folder) {
        List<Autosave> claimed = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return claimed;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            for (Path entry : entries) {
                String name = entry.toFile().getName();
                if (name.endsWith(LOCK)) {
                    String id = name.substring(0, name.length() - LOCK.length());
                    Autosave autosave = OWNED.contains(id) ? null : claim(folder, id);
                    if (autosave != null) {
                        claimed.add(autosave);
                    }
                } else if (name.endsWith(NEW_LOCK)
                        && !OWNED.contains(name.substring(0, name.length() - NEW_LOCK.length()))
                        && isStale(entry)) {
                    deleteIfAbandoned(entry);
                }
            }
        } catch (IOException e) {
            logger.error("Could not look for autosaves in " + folder, e);
        }
        claimed.sort(Comparator.comparing(Autosave::modified));
        return claimed;
    }

    /**
     * Takes one autosave if no running Watavyu owns it.
     *
     * @return the autosave, or null if another owns it or it holds nothing
     */
    private static Autosave claim(final Path folder, final String id) {
        Autosave autosave;
        try {
            autosave = locked(folder, id,
                FileChannel.open(folder.resolve(id + LOCK), StandardOpenOption.READ, StandardOpenOption.WRITE));
        } catch (IOException e) {
            logger.error("Could not open the autosave lock " + id + " in " + folder, e);
            return null;
        }
        if (autosave == null) {
            return null;
        }
        try {
            autosave.contents = Fingerprint.ofFile(autosave.getFile());
        } catch (IOException e) {
            logger.error("Could not read the autosave " + autosave.getFile(), e);
            autosave.release();
            return null;
        }
        if (autosave.contents == null) {
            autosave.delete();
            return null;
        }
        autosave.readLock();
        return autosave;
    }

    /**
     * Locks an autosave's lock file, which then belongs to the autosave returned; a lock file held elsewhere is
     * closed.
     *
     * @return the autosave, holding nothing yet, or null if the lock is held elsewhere
     */
    private static Autosave locked(final Path folder, final String id, final FileChannel channel) {
        try {
            FileLock lock = tryLock(channel);
            if (lock != null) {
                return new Autosave(folder, id, channel, lock);
            }
        } catch (IOException e) {
            logger.error("Could not lock the autosave " + id + " in " + folder, e);
        }
        closeQuietly(channel);
        return null;
    }

    /**
     * Locks a lock file, unless another Watavyu, or another part of this one, holds it.
     *
     * @return the lock, or null if it is held
     */
    private static FileLock tryLock(final FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException e) {
            // This Watavyu holds it already, for an autosave it is making.
            return null;
        }
    }

    /**
     * Returns whether a lock file not yet in place is old enough to have been left. One just made may not be locked
     * yet, and opening it then could keep the Watavyu making it from locking it.
     */
    private static boolean isStale(final Path newLock) {
        try {
            return Files.getLastModifiedTime(newLock).toMillis() < System.currentTimeMillis() - STALE_MILLIS;
        } catch (IOException e) {
            return false;
        }
    }

    /** Deletes a lock file left before it was put in place, unless the Watavyu making it is still at it. */
    private static void deleteIfAbandoned(final Path newLock) {
        try (FileChannel channel = FileChannel.open(newLock, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            if (tryLock(channel) != null) {
                Files.deleteIfExists(newLock);
            }
        } catch (IOException e) {
            logger.warn("Could not clear the unfinished autosave lock " + newLock, e);
        }
    }

    /**
     * Reads where the work came from and the spreadsheet's name out of the lock file. A lock file that cannot be read
     * leaves both null, so the work opens as untitled.
     */
    private void readLock() {
        try {
            int size = Math.toIntExact(channel.size());
            ByteBuffer buffer = ByteBuffer.allocate(size);
            int read = 0;
            while (read < size) {
                int more = channel.read(buffer, read);
                if (more < 0) {
                    break;
                }
                read += more;
            }
            JsonNode root = MAPPER.readTree(buffer.array(), 0, read);
            if (root == null) {
                return;
            }
            projectName = text(root, "name");
            String projectFile = text(root, "projectFile");
            if (projectFile != null) {
                origin = new Origin(new File(projectFile), text(root, "fingerprint"));
            }
        } catch (IOException | ArithmeticException e) {
            logger.warn("Could not read where an autosave came from; it opens as untitled", e);
        }
    }

    /** Returns a field's text, or null if it is missing or null. */
    private static String text(final JsonNode root, final String field) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * Saves the spreadsheet's work, replacing what this autosave held only once it is complete.
     *
     * @param project the project file's contents
     * @param from    where the work came from, or null for an untitled spreadsheet
     * @param name    the spreadsheet's name, or null if it has none
     * @throws IOException if it cannot be saved; the autosave keeps what it held
     */
    public void write(final String project, final Origin from, final String name) throws IOException {
        if (contents == null || !Objects.equals(origin, from) || !Objects.equals(projectName, name)) {
            writeLock(from, name);
        }
        Path partial = folder.resolve(id + PARTIAL);
        // On disk before it replaces the last autosave, so a power cut cannot leave the autosave empty.
        try (FileChannel out = FileChannel.open(partial, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(project.getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) {
                out.write(buffer);
            }
            out.force(true);
        }
        Files.move(partial, getFile(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        contents = Fingerprint.of(project);
    }

    private void writeLock(final Origin from, final String name) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("projectFile", from == null ? null : from.projectFile().getAbsolutePath());
        root.put("fingerprint", from == null ? null : from.fingerprint());
        root.put("name", name);
        channel.truncate(0);
        ByteBuffer buffer = ByteBuffer.wrap(MAPPER.writeValueAsBytes(root));
        while (buffer.hasRemaining()) {
            channel.write(buffer, buffer.position());
        }
        channel.force(false);
        origin = from;
        projectName = name;
    }

    /** Returns the autosave's project file; it exists once the autosave has been written. */
    public Path getFile() {
        return folder.resolve(id + ProjectDocument.EXTENSION);
    }

    /** Returns where the work came from, or null for an untitled spreadsheet. */
    public Origin getOrigin() {
        return origin;
    }

    /** Returns the spreadsheet's name, or null if it has none. */
    public String getProjectName() {
        return projectName;
    }

    /** Returns the fingerprint of the project file last written, or null before the first. */
    public String getContents() {
        return contents;
    }

    /** Returns when the project file was last written, or the earliest time if it has not been. */
    private FileTime modified() {
        try {
            return Files.getLastModifiedTime(getFile());
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    /** Deletes the autosave, project file then lock file, as its work was saved or knowingly discarded. */
    public void delete() {
        deleteQuietly(getFile());
        deleteQuietly(folder.resolve(id + PARTIAL));
        deleteQuietly(folder.resolve(id + LOCK));
        release();
    }

    /** Lets go of the autosave and leaves it on disk, so a later Watavyu can recover it. */
    public void release() {
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException e) {
            logger.warn("Could not unlock the autosave " + id, e);
        }
        closeQuietly(channel);
        OWNED.remove(id);
    }

    private static void deleteQuietly(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("Could not delete " + file, e);
        }
    }

    private static void closeQuietly(final FileChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            logger.warn("Could not close an autosave's lock file", e);
        }
    }
}

package org.datavyu.projectfile;

/** A project file that cannot be read or written. The message says where and why, for the user. */
public final class ProjectFileException extends Exception {

    private static final long serialVersionUID = 1L;

    public ProjectFileException(final String message) {
        super(message);
    }

    public ProjectFileException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

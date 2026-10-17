package org.datavyu.util.FileFilters;

import org.datavyu.projectfile.ProjectDocument;

import javax.swing.filechooser.FileFilter;
import java.io.File;

/** Watavyu project files ({@code .watavyu.json}). */
public final class ProjectFileFilter extends FileFilter {

    public static final ProjectFileFilter INSTANCE = new ProjectFileFilter();

    private ProjectFileFilter() {
    }

    @Override
    public boolean accept(final File file) {
        return file.getName().endsWith(ProjectDocument.EXTENSION) || file.isDirectory();
    }

    @Override
    public String getDescription() {
        return "Watavyu project (*" + ProjectDocument.EXTENSION + ")";
    }
}

/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.controllers;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import org.datavyu.models.db.UserWarningException;
import org.datavyu.projectfile.ProjectDocument;
import org.datavyu.projectfile.ProjectDocument.AnnotationCell;
import org.datavyu.projectfile.ProjectDocument.AnnotationColumn;
import org.datavyu.projectfile.ProjectJson;
import org.datavyu.util.Settings;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Tests for saving project files.
 */
public class SaveControllerTest {

    private static final ProjectDocument PROJECT = new ProjectDocument(List.of(
        new AnnotationColumn("TestColumn", List.of("var"), false,
            List.of(new AnnotationCell(60_000, 120_000, List.of("This is a test cell."))))));

    @Test
    public void aSavedProjectReopensAndIsARecentProject() throws Exception {
        File file = Files.createTempDirectory("save").resolve("study" + ProjectDocument.EXTENSION).toFile();

        new SaveController().saveProject(file, PROJECT);

        assertEquals(ProjectJson.read(file.toPath()), PROJECT);
        assertEquals(Settings.get().getRecentProjects().get(0), file.getAbsoluteFile());
    }

    @Test
    public void aProjectThatCannotBeSavedSaysWhy() throws Exception {
        File file = Files.createTempDirectory("save").resolve("study" + ProjectDocument.EXTENSION).toFile();
        ProjectDocument tooManyValues = new ProjectDocument(List.of(new AnnotationColumn("TestColumn", List.of("var"),
            false, List.of(new AnnotationCell(1_000, 2_000, List.of("a", "b"))))));

        UserWarningException e = expectThrows(UserWarningException.class,
            () -> new SaveController().saveProject(file, tooManyValues));

        assertTrue(e.getMessage().contains("columns[0].cells[0].values"), e.getMessage());
        assertTrue(!file.exists());
    }
}

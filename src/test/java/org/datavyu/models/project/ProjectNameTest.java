package org.datavyu.models.project;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class ProjectNameTest {

    @Test
    public void aProjectIsNamedAfterItsFile() {
        Project project = new Project();

        project.setProjectName("/Users/someone/study/session-04.watavyu.json");
        assertEquals(project.getProjectName(), "session-04");

        project.setProjectName("Study.WATAVYU.JSON");
        assertEquals(project.getProjectName(), "Study");

        project.setProjectName("old.csv");
        assertEquals(project.getProjectName(), "old");
    }
}

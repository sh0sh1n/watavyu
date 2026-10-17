package org.datavyu;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.datavyu.UnsavedWork.Choice;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;

/** B1: quitting or closing a tab never discards changes the user did not choose to discard. */
public class UnsavedWorkTest {

    private static final String[] OPTIONS = {"Save", "Cancel", "Don't save"};

    @Test
    public void theAnswerIsTheLabelOfTheButtonChosen() {
        assertEquals(UnsavedWork.choice(0, OPTIONS, "Save", "Don't save"), Choice.SAVE);
        assertEquals(UnsavedWork.choice(1, OPTIONS, "Save", "Don't save"), Choice.CANCEL);
        assertEquals(UnsavedWork.choice(2, OPTIONS, "Save", "Don't save"), Choice.DISCARD);
    }

    @Test
    public void closingTheDialogCancels() {
        assertEquals(UnsavedWork.choice(JOptionPane.CLOSED_OPTION, OPTIONS, "Save", "Don't save"), Choice.CANCEL);
    }

    private final List<String> asked = new ArrayList<>();
    private final List<String> saved = new ArrayList<>();

    private boolean mayClose(final Map<String, Choice> answers, final boolean saveWorks, final String... documents) {
        asked.clear();
        saved.clear();
        return UnsavedWork.mayClose(List.of(documents), answers::containsKey, document -> {
            asked.add(document);
            return answers.get(document);
        }, document -> {
            saved.add(document);
            return saveWorks;
        });
    }

    @Test
    public void everyChangedDocumentIsAskedAbout() {
        assertTrue(mayClose(Map.of("a", Choice.DISCARD, "c", Choice.SAVE), true, "a", "b", "c"));
        assertEquals(asked, List.of("a", "c"));
        assertEquals(saved, List.of("c"));
    }

    @Test
    public void cancellingStopsTheQuit() {
        assertFalse(mayClose(Map.of("a", Choice.CANCEL, "b", Choice.DISCARD), true, "a", "b"));
        assertEquals(asked, List.of("a"));
    }

    @Test
    public void aSaveThatDoesNotHappenStopsTheQuit() {
        assertFalse(mayClose(Map.of("a", Choice.SAVE, "b", Choice.DISCARD), false, "a", "b"));
        assertEquals(asked, List.of("a"));
    }
}

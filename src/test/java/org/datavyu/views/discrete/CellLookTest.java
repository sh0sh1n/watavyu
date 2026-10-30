package org.datavyu.views.discrete;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.datavyu.views.discrete.CellLook.Colours;
import org.datavyu.views.discrete.CellLook.Selection;
import org.datavyu.views.discrete.CellLook.Time;
import org.testng.annotations.Test;

import javax.swing.JPanel;
import javax.swing.border.Border;
import java.awt.Insets;

/**
 * How a cell looks against the needle and its selection: the cell in play is lifted and marked rather than filled,
 * a past cell's text fades by the setting, and no state moves the cell's text.
 */
public class CellLookTest {

    private static final Palette PAPER = Palette.PAPER;

    @Test
    public void aCellIsInPlayFromItsOnsetToItsOffset() {
        assertEquals(CellLook.timeOf(1_000, 2_000, 999), Time.AHEAD);
        assertEquals(CellLook.timeOf(1_000, 2_000, 1_000), Time.IN_PLAY);
        assertEquals(CellLook.timeOf(1_000, 2_000, 2_000), Time.IN_PLAY);
        assertEquals(CellLook.timeOf(1_000, 2_000, 2_001), Time.PAST);
    }

    @Test
    public void aPointCellIsInPlayOnlyAtItsTime() {
        assertEquals(CellLook.timeOf(1_000, 1_000, 999), Time.AHEAD);
        assertEquals(CellLook.timeOf(1_000, 1_000, 1_000), Time.IN_PLAY);
        assertEquals(CellLook.timeOf(1_000, 1_000, 1_001), Time.PAST);
    }

    @Test
    public void aCellWhoseOffsetIsBeforeItsOnsetIsNeverInPlay() {
        assertEquals(CellLook.timeOf(2_000, 1_000, 1_500), Time.AHEAD);
        assertEquals(CellLook.timeOf(2_000, 1_000, 2_000), Time.AHEAD);
        assertEquals(CellLook.timeOf(2_000, 1_000, 2_001), Time.PAST);
    }

    @Test
    public void aCellAheadHasThePlainColours() {
        assertEquals(CellLook.colours(PAPER, Time.AHEAD, 60),
                new Colours(PAPER.cell(), PAPER.text(), PAPER.time(), PAPER.ordinal()));
    }

    @Test
    public void theCellInPlayIsLiftedWithItsTextAtFullStrength() {
        assertEquals(CellLook.colours(PAPER, Time.IN_PLAY, 60),
                new Colours(PAPER.inPlay(), PAPER.text(), PAPER.time(), PAPER.ordinal()));
    }

    @Test
    public void aPastCellFadesByTheSetting() {
        assertEquals(CellLook.colours(PAPER, Time.PAST, 40), new Colours(PAPER.cell(),
                PAPER.fade(PAPER.text(), 40), PAPER.fade(PAPER.time(), 40), PAPER.fade(PAPER.ordinal(), 40)));
    }

    @Test
    public void dimmingAtZeroLeavesPastCellsAsTheyWere() {
        assertEquals(CellLook.colours(PAPER, Time.PAST, 0), CellLook.colours(PAPER, Time.AHEAD, 0));
    }

    @Test
    public void noStateFillsTheCellWithColour() {
        for (Time time : Time.values()) {
            Colours colours = CellLook.colours(PAPER, time, 100);
            double lift = PaletteTest.contrast(colours.background(), PAPER.cell());
            assertTrue(lift <= 1.15, time + " " + lift);
        }
    }

    @Test
    public void everyBorderKeepsTheTextInPlace() {
        JPanel cell = new JPanel();
        Insets plain = CellLook.border(PAPER, Selection.NONE, false, false).getBorderInsets(cell);
        for (Palette palette : new Palette[]{Palette.PAPER, Palette.GRAPHITE}) {
            for (Selection selection : Selection.values()) {
                for (boolean overlap : new boolean[]{false, true}) {
                    for (boolean inPlay : new boolean[]{false, true}) {
                        Border border = CellLook.border(palette, selection, overlap, inPlay);
                        assertEquals(border.getBorderInsets(cell), plain,
                                palette.name() + " " + selection + " overlap=" + overlap + " inPlay=" + inPlay);
                    }
                }
            }
        }
    }

    @Test
    public void theSameLookGivesTheSameBorder() {
        assertSame(CellLook.border(PAPER, Selection.SELECTED, true, false),
                CellLook.border(PAPER, Selection.SELECTED, true, false));
        assertNotSame(CellLook.border(PAPER, Selection.NONE, false, true),
                CellLook.border(PAPER, Selection.NONE, false, false));
        assertNotSame(CellLook.border(Palette.GRAPHITE, Selection.NONE, false, false),
                CellLook.border(PAPER, Selection.NONE, false, false));
    }
}

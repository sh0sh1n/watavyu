package org.datavyu.util;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class CitationTest {

    private static Citation parse(String cff) {
        return Citation.fromCff(cff);
    }

    private static final String BASE = "message: \"Cite me.\"\n"
        + "title: \"Watavyu\"\n"
        + "repository-code: \"https://github.com/sh0sh1n/watavyu\"\n";

    /** The bundled CITATION.cff, which GitHub's "Cite this repository" also reads. */
    @Test
    public void bundledCitation() {
        Citation citation = Citation.load();
        assertEquals(citation.getMessage(), "If you use Watavyu in your research, please cite it as below.");
        assertEquals(citation.apa("1.2.3"),
            "The Watavyu Contributors. (n.d.). Watavyu (Version 1.2.3) [Computer software]. "
                + "https://github.com/sh0sh1n/watavyu");
        assertEquals(citation.bibtex("1.2.3"),
            "@software{watavyu,\n"
                + "  author = {{The Watavyu Contributors}},\n"
                + "  title = {Watavyu},\n"
                + "  version = {1.2.3},\n"
                + "  url = {https://github.com/sh0sh1n/watavyu}\n"
                + "}");
    }

    @Test
    public void releaseDateGivesTheYear() {
        Citation unquoted = parse(BASE + "date-released: 2027-01-15\nauthors:\n  - name: \"Group\"\n");
        Citation quoted = parse(BASE + "date-released: \"2027-01-15\"\nauthors:\n  - name: \"Group\"\n");
        assertEquals(unquoted.apa("1.0.0"),
            "Group. (2027). Watavyu (Version 1.0.0) [Computer software]. https://github.com/sh0sh1n/watavyu");
        assertEquals(quoted.apa("1.0.0"), unquoted.apa("1.0.0"));
        assertEquals(unquoted.bibtex("1.0.0"),
            "@software{watavyu,\n  author = {{Group}},\n  title = {Watavyu},\n  year = {2027},\n"
                + "  version = {1.0.0},\n  url = {https://github.com/sh0sh1n/watavyu}\n}");
    }

    @Test
    public void bibtexKeyAndSpecialCharacters() {
        Citation citation = parse("message: \"m\"\ntitle: \"Watavyu: A Video Coding Tool\"\n"
            + "repository-code: \"https://example.org/a_b\"\nauthors:\n  - name: \"Lab A & B\"\n");
        assertEquals(citation.bibtex("1.0.0"),
            "@software{watavyu,\n  author = {{Lab A \\& B}},\n  title = {Watavyu: A Video Coding Tool},\n"
                + "  version = {1.0.0},\n  url = {https://example.org/a_b}\n}");
        assertEquals(citation.apa("1.0.0"),
            "Lab A & B. (n.d.). Watavyu: A Video Coding Tool (Version 1.0.0) [Computer software]. "
                + "https://example.org/a_b");
    }

    @Test
    public void peopleAreFormattedPerStyle() {
        Citation citation = parse(BASE + "authors:\n"
            + "  - given-names: \"Ada Maria\"\n    family-names: \"Lovelace\"\n"
            + "  - given-names: \"Alan\"\n    family-names: \"Turing\"\n");
        assertEquals(citation.apa("1.0.0"),
            "Lovelace, A. M., & Turing, A. (n.d.). Watavyu (Version 1.0.0) [Computer software]. "
                + "https://github.com/sh0sh1n/watavyu");
        assertEquals(citation.bibtex("1.0.0"),
            "@software{watavyu,\n  author = {Lovelace, Ada Maria and Turing, Alan},\n  title = {Watavyu},\n"
                + "  version = {1.0.0},\n  url = {https://github.com/sh0sh1n/watavyu}\n}");
    }

    /** Only the citation's keys are read: comments, other keys and other lists are skipped, and quotes are undone. */
    @Test
    public void readsOnlyWhatACitationNeeds() {
        Citation citation = parse("# A comment\ncff-version: 1.2.0\nmessage: 'It''s \"cited\".'\n"
            + "title: \"Say \\\"Watavyu\\\"\"\nkeywords:\n  - video\n  - coding\n"
            + "repository-code: https://example.org\n"
            + "authors:\n  # people\n  - family-names: Lovelace\n    given-names: Ada\nlicense: GPL-3.0-or-later\n");
        assertEquals(citation.getMessage(), "It's \"cited\".");
        assertEquals(citation.apa("1.0.0"),
            "Lovelace, A. (n.d.). Say \"Watavyu\" (Version 1.0.0) [Computer software]. https://example.org");
    }
}

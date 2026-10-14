package org.datavyu.util;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How to cite Watavyu, read from the CITATION.cff file at the repository root (bundled as a resource).
 *
 * The running application's version is cited rather than any version in the file, so a citation always names the
 * build that produced the results.
 */
public final class Citation {

    private static final String RESOURCE = "/CITATION.cff";

    private final String message;
    private final String title;
    /** Each author as read from the file: an entity has "name", a person has "given-names" and "family-names". */
    private final List<Map<?, ?>> authors;
    private final String url;
    private final String year;

    private Citation(String message, String title, List<Map<?, ?>> authors, String url, String year) {
        this.message = message;
        this.title = title;
        this.authors = authors;
        this.url = url;
        this.year = year;
    }

    /**
     * Reads the bundled CITATION.cff.
     *
     * @return The citation.
     */
    public static Citation load() {
        try (InputStream in = Citation.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            return fromCff((Map<?, ?>) new Yaml().load(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    /**
     * Builds a citation from parsed CITATION.cff contents.
     *
     * @param cff The YAML document as a map.
     *
     * @return The citation.
     */
    static Citation fromCff(Map<?, ?> cff) {
        List<Map<?, ?>> authors = new ArrayList<>();
        for (Object author : (List<?>) cff.get("authors")) {
            authors.add((Map<?, ?>) author);
        }
        return new Citation((String) cff.get("message"), (String) cff.get("title"), authors,
            (String) cff.get("repository-code"), year(cff.get("date-released")));
    }

    /** SnakeYAML reads an unquoted date as a Date at midnight UTC and a quoted one as a String. */
    private static String year(Object dateReleased) {
        if (dateReleased == null) {
            return null;
        }
        if (dateReleased instanceof Date) {
            return String.valueOf(((Date) dateReleased).toInstant().atZone(ZoneOffset.UTC).getYear());
        }
        return dateReleased.toString().substring(0, 4);
    }

    /**
     * The request to cite, for example "If you use Watavyu in your research, please cite it as below."
     *
     * @return The message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * APA 7 style reference for software.
     *
     * @param version The cited version.
     *
     * @return For example "The Watavyu Contributors. (n.d.). Watavyu (Version 0.1.0) [Computer software]. URL"
     */
    public String apa(String version) {
        List<String> names = new ArrayList<>();
        for (Map<?, ?> author : authors) {
            if (author.containsKey("name")) {
                names.add((String) author.get("name"));
            } else {
                StringBuilder initials = new StringBuilder();
                for (String given : ((String) author.get("given-names")).split(" ")) {
                    initials.append(initials.length() == 0 ? "" : " ").append(given.charAt(0)).append('.');
                }
                names.add(author.get("family-names") + ", " + initials);
            }
        }
        String joined = names.size() == 1 ? names.get(0)
            : String.join(", ", names.subList(0, names.size() - 1)) + ", & " + names.get(names.size() - 1);
        return joined + (joined.endsWith(".") ? "" : ".") + " (" + (year == null ? "n.d." : year) + "). " + title
            + " (Version " + version + ") [Computer software]. " + url;
    }

    /**
     * BibLaTeX @software entry. A group author is double-braced so BibTeX does not split it into first and last
     * names.
     *
     * @param version The cited version.
     *
     * @return The entry.
     */
    public String bibtex(String version) {
        List<String> names = new ArrayList<>();
        for (Map<?, ?> author : authors) {
            names.add(author.containsKey("name")
                ? "{" + escape((String) author.get("name")) + "}"
                : escape(author.get("family-names") + ", " + author.get("given-names")));
        }
        StringBuilder entry = new StringBuilder("@software{").append(key()).append(",\n");
        entry.append("  author = {").append(String.join(" and ", names)).append("},\n");
        entry.append("  title = {").append(escape(title)).append("},\n");
        if (year != null) {
            entry.append("  year = {").append(year).append("},\n");
        }
        entry.append("  version = {").append(version).append("},\n");
        entry.append("  url = {").append(url).append("}\n");
        return entry.append("}").toString();
    }

    /** The citation key: the title's first word, lowercased, since keys cannot contain spaces or punctuation. */
    private String key() {
        String[] words = title.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String word : words) {
            if (!word.isEmpty()) {
                return word;
            }
        }
        return "software";
    }

    /** Escapes characters that are special in BibTeX field text. The url field is left as is, as BibLaTeX expects. */
    private static String escape(String text) {
        return text.replaceAll("([&%$#_])", "\\\\$1");
    }
}

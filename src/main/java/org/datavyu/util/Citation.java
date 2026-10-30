package org.datavyu.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How to cite Watavyu, read from the CITATION.cff file at the repository root (bundled as a resource). The file is
 * YAML; this reads the part of it a citation needs, as the file writes it: top-level {@code key: value} lines, and the
 * {@code authors} list of {@code - key: value} entries. Other keys and nested blocks are skipped.
 *
 * The running application's version is cited rather than any version in the file, so a citation always names the
 * build that produced the results.
 */
public final class Citation {

    private static final String RESOURCE = "/CITATION.cff";

    private final String message;
    private final String title;
    /** Each author as read from the file: an entity has "name", a person has "given-names" and "family-names". */
    private final List<Map<String, String>> authors;
    private final String url;
    private final String year;

    private Citation(String message, String title, List<Map<String, String>> authors, String url, String year) {
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
            return fromCff(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    /**
     * Builds a citation from the text of a CITATION.cff file.
     *
     * @param cff The file's text.
     *
     * @return The citation.
     */
    static Citation fromCff(String cff) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<Map<String, String>> authors = new ArrayList<>();
        String block = null;
        for (String line : cff.lines().toList()) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            if (!Character.isWhitespace(line.charAt(0))) {
                String[] field = field(line);
                block = field[1].isEmpty() ? field[0] : null;
                fields.put(field[0], field[1]);
            } else if ("authors".equals(block)) {
                String entry = line.strip();
                if (entry.startsWith("-")) {
                    authors.add(new LinkedHashMap<>());
                    entry = entry.substring(1).strip();
                }
                if (!entry.isEmpty() && !authors.isEmpty()) {
                    String[] field = field(entry);
                    authors.get(authors.size() - 1).put(field[0], field[1]);
                }
            }
        }
        String released = fields.get("date-released");
        return new Citation(fields.get("message"), fields.get("title"), authors, fields.get("repository-code"),
            released == null || released.length() < 4 ? null : released.substring(0, 4));
    }

    /** Splits {@code key: value} into the key and the value, unquoted; a key alone has an empty value. */
    private static String[] field(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Not a key and value in CITATION.cff: " + line);
        }
        return new String[] {line.substring(0, colon).strip(), unquote(line.substring(colon + 1).strip())};
    }

    /** A double-quoted value with its backslash escapes, a single-quoted one with doubled quotes, or a plain one. */
    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replaceAll("\\\\(.)", "$1");
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
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
        for (Map<String, String> author : authors) {
            if (author.containsKey("name")) {
                names.add(author.get("name"));
            } else {
                StringBuilder initials = new StringBuilder();
                for (String given : author.get("given-names").split(" ")) {
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
        for (Map<String, String> author : authors) {
            names.add(author.containsKey("name")
                ? "{" + escape(author.get("name")) + "}"
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

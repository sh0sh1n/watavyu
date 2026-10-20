package org.datavyu.projectfile;

import com.fasterxml.jackson.databind.JsonNode;
import org.datavyu.util.Timestamp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Strict access to the fields of a JSON tree: each method names the place in the file when a field is wrong. */
final class JsonFields {

    private JsonFields() {
    }

    /** Requires an object with exactly these keys. */
    static void keys(final JsonNode node, final String at, final String... keys) throws ProjectFileException {
        keys(node, at, Set.of(), keys);
    }

    /** Requires an object with these keys and no others, all of them present except the optional ones. */
    static void keys(final JsonNode node, final String at, final Set<String> optional, final String... keys)
            throws ProjectFileException {
        object(node, at);
        Set<String> expected = Set.of(keys);
        for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!expected.contains(name)) {
                throw new ProjectFileException(at + ": unknown key \"" + name + "\"");
            }
        }
        for (String key : keys) {
            if (!node.has(key) && !optional.contains(key)) {
                throw new ProjectFileException(at + ": \"" + key + "\" is missing");
            }
        }
    }

    static void object(final JsonNode node, final String at) throws ProjectFileException {
        if (!node.isObject()) {
            throw new ProjectFileException(at + ": must be an object");
        }
    }

    static JsonNode array(final JsonNode node, final String at) throws ProjectFileException {
        if (!node.isArray()) {
            throw new ProjectFileException(at + ": must be an array");
        }
        return node;
    }

    static String text(final JsonNode node, final String at) throws ProjectFileException {
        if (!node.isTextual()) {
            throw new ProjectFileException(at + ": must be a string");
        }
        return node.textValue();
    }

    static List<String> texts(final JsonNode node, final String at) throws ProjectFileException {
        array(node, at);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            texts.add(text(node.get(i), at + "[" + i + "]"));
        }
        return texts;
    }

    static boolean bool(final JsonNode node, final String at) throws ProjectFileException {
        if (!node.isBoolean()) {
            throw new ProjectFileException(at + ": must be true or false");
        }
        return node.booleanValue();
    }

    static double number(final JsonNode node, final String at) throws ProjectFileException {
        if (!node.isNumber()) {
            throw new ProjectFileException(at + ": must be a number");
        }
        return node.doubleValue();
    }

    static int integer(final JsonNode node, final String at) throws ProjectFileException {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new ProjectFileException(at + ": must be a whole number");
        }
        return node.intValue();
    }

    static long time(final JsonNode node, final String at) throws ProjectFileException {
        try {
            return Timestamp.parseCanonical(text(node, at));
        } catch (IllegalArgumentException e) {
            throw new ProjectFileException(at + ": \"" + node.textValue() + "\" is not a time (HH:MM:SS:mmm)", e);
        }
    }
}

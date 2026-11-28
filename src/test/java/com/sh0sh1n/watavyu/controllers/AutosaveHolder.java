package com.sh0sh1n.watavyu.controllers;

import java.nio.file.Paths;

/**
 * Stands in for a running Watavyu in {@link AutosaveTest}: writes an autosave in the folder given, says so, and holds
 * it until killed.
 */
public final class AutosaveHolder {

    /** Printed once the autosave is written */
    static final String READY = "autosave written";

    /** The autosave's project file */
    static final String PROJECT = "{\"format\": \"watavyu\", \"version\": 1, \"columns\": []}\n";

    private AutosaveHolder() {
    }

    /**
     * Writes an autosave and waits to be killed.
     *
     * @param args the folder autosaves are kept in
     * @throws Exception if the autosave cannot be written
     */
    public static void main(final String[] args) throws Exception {
        Autosave.create(Paths.get(args[0])).write(PROJECT, null);
        System.out.println(READY);
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
    }
}

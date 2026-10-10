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
package org.datavyu.plugins;

import com.google.common.collect.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.Datavyu;
import org.datavyu.plugins.javafx.JavaFxVideoPlugin;
import org.datavyu.util.MacOS;

import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.lang.reflect.Modifier;
import java.util.*;


/**
 * This class manages and wrangles all the viewer plugins currently available in Datavyu. It is implemented as a
 * singleton, so only one instance is available to Datavyu. This single instance will load all plugins that implement
 * the Plugin interface.
 */
public final class PluginManager {

    /* WARNING: pluginClass, static { pluginClass }, logger and pluginManager must appear in this order */
    /** A reference to the interface that plugins must override */
    private static final Class<?> pluginClass; // = Plugin.class;

    static {
        pluginClass = Plugin.class;
    }

    /** The logger for this class */
    private static Logger logger = LogManager.getLogger(PluginManager.class.getName());

    /** Single instance of the PluginManager for Datavyu */
    private static final PluginManager pluginManager = new PluginManager();

    /** Set of plugins */
    private Set<Plugin> plugins;

    /** Set of names of the plugins that we added */
    private Set<String> pluginNames;

    /** Mapping between plugin classifiers and plugins */
    private Multimap<String, Plugin> pluginClassifiers;

    /** The list of plugins associated with data viewer class name */
    private Map<String, Plugin> viewerClassToPlugin;

    /** Merge file filters for plugins of the same name */
    private Map<String, GroupFileFilter> filters;

    /**
     * Default constructor. Searches for valid plugins in the classpath by looking for classes that implement the
     * plugin interface.
     */
    private PluginManager() {
        plugins = Sets.newLinkedHashSet();
        pluginNames = Sets.newHashSet();
        viewerClassToPlugin = Maps.newHashMap();
        pluginClassifiers = HashMultimap.create();
        filters = Maps.newLinkedHashMap();
        initialize();
    }

    /**
     * Get the singleton instance of this PluginManager.
     *
     * @return The single instance of the PluginManager object in Datavyu.
     */
    public static PluginManager getInstance() {
        return pluginManager;
    }

    /**
     * Initializes the plugin manager by searching for valid plugins to insert into the manager.
     */
    private void initialize() {
        logger.info("Initializing the PluginManager");
        try {
            addPlugin(JavaFxVideoPlugin.class.getName());
        } catch (Exception e) {
            logger.error("Unable to load plugin", e);
        }
    }

    /**
     * Attempts to add an instance of the supplied class name as a plugin to the plugin manager. Will only add the
     * class if it implements the plugin interface.
     *
     * @param className The fully qualified class name to attempt to add to the list of plugins.
     */
    private void addPlugin(final String className) {
        try {
            String cName = className.replaceAll("\\.class$", "").replace('/','.');
            /*
             * Ignore classes that: - Belong to the UITests (traditionally this
             * was because of the UISpec4J interceptor, which interrupted the
             * UI. We still ignore UITest classes as these will not be plugins)
             * - Are part of GStreamer, or JUnitX (these cause issues and are
             * certainly not going to be plugins either)
             */
            if (!cName.contains("org.datavyu.uitests") && !cName.contains("org.gstreamer")
                    && !cName.contains("ch.randelshofer") && !cName.startsWith("junitx")) {

                logger.info("Loading " + cName);
                Class<?> testClass = Class.forName(cName);

                if (pluginClass.isAssignableFrom(testClass) &&
                        (testClass.getModifiers() & (Modifier.ABSTRACT | Modifier.INTERFACE)) == 0)
                {
                    Plugin plugin = (Plugin) testClass.newInstance();

                    if (!plugin.getValidPlatforms().contains(Datavyu.getPlatform())) {
                        // Not valid for this operating system
                        return;
                    }

                    if (Datavyu.getPlatform() == Datavyu.Platform.MAC) {
                        if (!plugin.getValidVersions().isInRange(MacOS.getOSVersion())) {
                            return;
                        }
                    }

                    String pluginName = plugin.getPluginName();

                    if (pluginNames.contains(plugin.getPluginName())) {

                        // We already have this plugin; stop processing it
                        return;
                    }

                    pluginNames.add(pluginName);

                    buildGroupFilter(plugin);

                    // Ensure we have at least one file filter
                    assert plugin.getFilters() != null;
                    assert plugin.getFilters().length > 0;
                    assert plugin.getFilters()[0] != null;

                    plugins.add(plugin);

                    // BugzID:2110
                    pluginClassifiers.put(plugin.getNamespace(), plugin);

                    final Class<? extends StreamViewer> cdv = plugin.getViewerClass();

                    if (cdv != null) {
                        viewerClassToPlugin.put(cdv.getName(), plugin);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            logger.error("Unable to find plugin. Error: ", e);
        } catch (ClassFormatError e) {
            logger.error("Plugin with bad class format. Error: ", e);
        } catch (Exception e) {
            logger.error("Unable to instantiate plugin. Error: ", e);
        }
    }

    private void buildGroupFilter(final Plugin p) {

        for (Filter filter : p.getFilters()) {
            GroupFileFilter groupFileFilter;

            if (filters.containsKey(filter.getName())) {
                groupFileFilter = filters.get(filter.getName());
            } else {
                groupFileFilter = new GroupFileFilter(filter.getName());
                filters.put(filter.getName(), groupFileFilter);
            }

            groupFileFilter.addFileFilter(filter);
        }
    }

    public Iterable<? extends FileFilter> getFileFilters() {
        return filters.values();
    }

    public Iterable<Plugin> getPlugins() {
        return Lists.newArrayList(plugins);
    }

    /**
     * Searches for and returns a plugin compatible with the given classifier and data file
     *
     * @param classifier Plugin classifier string
     * @param file       The data file to open
     * @return A plugin whose file filter accepts the file, otherwise any plugin with the classifier, or null
     */
    public Plugin getCompatiblePlugin(final String classifier, final File file) {
        Collection<Plugin> candidates = pluginClassifiers.get(classifier);
        for (Plugin candidate : candidates) {
            for (Filter filter : candidate.getFilters()) {
                if (filter.getFileFilter().accept(file)) {
                    return candidate;
                }
            }
        }

        // No extension match: let a plugin of the same kind try, so it can explain why it fails.
        return candidates.isEmpty() ? null : candidates.iterator().next();
    }

    /**
     * @param dataViewer The fully-qualified class name of the data viewer
     *                   implementation
     * @return The {@link Plugin} used to build the data viewer if it exists,
     * {@code null} otherwise.
     */
    public Plugin getAssociatedPlugin(final String dataViewer) {
        return viewerClassToPlugin.get(dataViewer);
    }

    /** Given uuid, return associated plugin.
     *  @param uuid UUID of plugin.
     *  @return plugin Associated plugin.
     */
    public Plugin getPluginFromUUID(UUID uuid){
        for(Plugin p : getPlugins()) {
            if(p.getPluginUUID().equals(uuid)) return p;
        }
        return null;
    }
}

package org.apache.sling.maven.projectsupport;

import static org.apache.felix.framework.util.FelixConstants.*;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.felix.framework.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.PropertyUtils;
import org.apache.sling.launchpad.base.impl.ResourceProvider;
import org.apache.sling.launchpad.base.impl.Sling;
import org.apache.sling.launchpad.base.shared.Notifiable;
import org.apache.sling.launchpad.base.shared.SharedConstants;
import org.apache.sling.maven.projectsupport.bundlelist.v1_0_0.Bundle;
import org.apache.sling.maven.projectsupport.bundlelist.v1_0_0.BundleList;
import org.apache.sling.maven.projectsupport.bundlelist.v1_0_0.StartLevel;
import org.osgi.framework.BundleException;

/**
 * @author justin
 *
 */
public abstract class AbstractLaunchpadStartingPlugin extends AbstractBundleListMojo  implements Notifiable {

    /** Default log level setting if no set on command line (value is "INFO"). */
    private static final int DEFAULT_LOG_LEVEL = Logger.LOG_INFO;

    /** Mapping between log level numbers and names */
    private static final String[] logLevels = { "FATAL", "ERROR", "WARN", "INFO", "DEBUG" };

    /**
     * The configuration property setting the port on which the HTTP service
     * listens
     */
    private static final String PROP_PORT = "org.osgi.service.http.port";

    /** Return the log level code for the string */
    private static int toLogLevelInt(String level, int defaultLevel) {
        for (int i = 0; i < logLevels.length; i++) {
            if (logLevels[i].equalsIgnoreCase(level)) {
                return i;
            }
        }

        return defaultLevel;
    }

    /**
     * @parameter expression="${http.port}" default-value="8080"
     */
    private int httpPort;

    /**
     * The definition of the package to be included to provide web support for
     * JAR-packaged projects (i.e. pax-web).
     *
     * @parameter
     */
    private ArtifactDefinition jarWebSupport;

    /**
     * @parameter expression="${felix.log.level}"
     */
    private String logLevel;

    /**
     * @parameter expression="${propertiesFile}"
     *            default-value="src/test/config/sling.properties"
     */
    private File propertiesFile;

    /**
     * @component
     */
    private MavenFileFilter mavenFileFilter;

    /**
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    private MavenSession mavenSession;

    private ResourceProvider resourceProvider = new ResourceProvider() {

        @Override
        public Iterator<String> getChildren(String path) {
            if (path.equals("resources/bundles")) {
                List<String> levels = new ArrayList<String>();
                for (StartLevel level : getBundleList().getStartLevels()) {
                    levels.add(String.valueOf(level.getLevel()) + "/");
                }
                return levels.iterator();
            } else if (path.equals("resources/corebundles")) {
                List<String> empty = Collections.emptyList();
                return empty.iterator();
            } else {
                try {
                    int i = Integer.parseInt(path);
                    List<String> bundles = new ArrayList<String>();
                    for (StartLevel level : getBundleList().getStartLevels()) {
                        if (level.getLevel() == i) {
                            for (Bundle bundle : level.getBundles()) {
                                ArtifactDefinition d = new ArtifactDefinition(bundle, i);
                                try {
                                    Artifact artifact = getArtifact(d);
                                    bundles.add(artifact.getFile().toURI().toURL().toExternalForm());
                                } catch (Exception e) {
                                    getLog().error("Unable to resolve artifact ", e);
                                }
                            }

                            break;
                        }
                    }
                    return bundles.iterator();

                } catch (NumberFormatException e) {
                    getLog().warn("un-handlable path " + path);
                    return null;

                }
            }
        }

        @Override
        public URL getResource(String path) {
            if (path.endsWith(".properties")) {
                return getClass().getResource("/" + path);
            } else {
                try {
                    return new URL(path);
                } catch (MalformedURLException e) {
                    getLog().error("Expecting a real URL", e);
                    return null;
                }
            }
        }
    };

    private Sling sling;

    /**
     * @parameter expression="${sling.home}" default-value="sling"
     */
    private String slingHome;

    /**
     * @parameter default-value="true"
     */
    private boolean forceBundleLoad;

    public void stopped() {
        sling = null;
    }

    public void updated(File tmpFile) {
        // TODO - should anything happen here?
        getLog().info("File updated " + tmpFile.getAbsolutePath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void executeWithArtifacts() throws MojoExecutionException, MojoFailureException {
        try {
            final Map<String, String> props = new HashMap<String, String>();

            props.put(SharedConstants.SLING_HOME, slingHome);

            if (forceBundleLoad) {
                props.put(SharedConstants.FORCE_PACKAGE_BUNDLE_LOADING, "true");
            }

            // set up and configure Felix Logger
            int logLevelNum;
            if (logLevel == null) {
                logLevelNum = DEFAULT_LOG_LEVEL;
            } else {
                logLevelNum = toLogLevelInt(logLevel, DEFAULT_LOG_LEVEL);
            }
            props.put(LOG_LEVEL_PROP, String.valueOf(logLevelNum));
            // Display port number on console, in case HttpService doesn't
            getLog().info("HTTP server port: " + httpPort);
            props.put(PROP_PORT, String.valueOf(httpPort));

            // prevent tons of needless WARN from the framework
            Logger logger = new Logger();
            logger.setLogLevel(Logger.LOG_ERROR);

            if (propertiesFile.exists()) {
                File tmp = null;
                try {
                    tmp = File.createTempFile("sling", "props");
                    mavenFileFilter.copyFile(propertiesFile, tmp, true, project, null, true, System
                            .getProperty("file.encoding"), mavenSession);
                    Properties loadedProps = PropertyUtils.loadPropertyFile(tmp, null);
                    for (Object key : loadedProps.keySet()) {
                        props.put((String) key, (String) loadedProps.get(key));
                    }
                } catch (IOException e) {
                    throw new MojoExecutionException("Unable to create filtered properties file", e);
                } catch (MavenFilteringException e) {
                    throw new MojoExecutionException("Unable to create filtered properties file", e);
                } finally {
                    if (tmp != null) {
                        tmp.delete();
                    }
                }
            }

            sling = startSling(resourceProvider, props, logger);

        } catch (BundleException be) {
            getLog().error("Failed to Start OSGi framework", be);
        }

    }

    protected abstract Sling startSling(ResourceProvider resourceProvider, Map<String, String> props, Logger logger) throws BundleException;

    protected void stopSling() {
        if (sling != null) {
            sling.destroy();
        }
    }

    protected void initArtifactDefinitions(Properties dependencies) {
        if (jarWebSupport == null) {
            jarWebSupport = new ArtifactDefinition();
        }
        jarWebSupport.initDefaults(dependencies.getProperty("jarWebSupport"));
    }

    /**
     * Add the JAR Web Support bundle to the bundle list.
     */
    @Override
    protected void initBundleList(BundleList bundleList) {
        bundleList.add(jarWebSupport.toBundle());
    }
}
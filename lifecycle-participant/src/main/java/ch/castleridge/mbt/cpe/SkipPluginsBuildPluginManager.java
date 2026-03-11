package ch.castleridge.mbt.cpe;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.MojoNotFoundException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Named()
@Singleton
public class SkipPluginsBuildPluginManager implements BuildPluginManager {
    @Inject()
    SkipPluginsBuildPluginManager(BuildPluginManager delegate) {
        this.delegate = delegate;
        logger.info("SkipPluginsBuildPluginManager initialized");
    }   

    private static final Logger logger = LoggerFactory.getLogger(SkipPluginsBuildPluginManager.class);

    private final BuildPluginManager delegate;

    @Override
    public void executeMojo(MavenSession session, MojoExecution execution) throws MojoFailureException, MojoExecutionException, PluginConfigurationException, PluginManagerException {

        Collection<String> skipped = getSkipList(session);

        String plugin = execution.getPlugin().getArtifactId();

        if (skipped.contains(plugin) || skipped.contains(shortName(plugin))) {

            logger.info("Skipping plugin execution: " + plugin + ":" + execution.getGoal());

            return;
        }
        logger.info("Executing plugin: " + plugin + ":" + execution.getGoal() + " for project " + session.getCurrentProject().getArtifactId());
        delegate.executeMojo(session, execution);
    }

    private Collection<String> getSkipList(MavenSession session) {
        return Arrays.asList("maven-compiler-plugin", "maven-surefire-plugin", "maven-enforcer-plugin");
    }

    private String shortName(String plugin) {
        return plugin.replace("maven-", "").replace("-plugin", "");
    }

    @Override
    public PluginDescriptor loadPlugin(Plugin plugin, List<RemoteRepository> repositories,
            RepositorySystemSession session) throws PluginNotFoundException, PluginResolutionException,
            PluginDescriptorParsingException, InvalidPluginDescriptorException {
        return delegate.loadPlugin(plugin, repositories, session);
    }

    @Override
    public MojoDescriptor getMojoDescriptor(Plugin plugin, String goal, List<RemoteRepository> repositories,
            RepositorySystemSession session) throws PluginNotFoundException, PluginResolutionException,
            PluginDescriptorParsingException, MojoNotFoundException, InvalidPluginDescriptorException {
        return delegate.getMojoDescriptor(plugin, goal, repositories, session);
    }

    @Override
    public ClassRealm getPluginRealm(MavenSession session, PluginDescriptor pluginDescriptor)
            throws PluginResolutionException, PluginManagerException {
        return delegate.getPluginRealm(session, pluginDescriptor);
    }
}
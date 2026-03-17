package ch.castleridge.mbt.cpe;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import ch.castleridge.mbt.cpe.json.MavenExtractedInfo;
import ch.castleridge.mbt.cpe.json.MavenTargetInfo;

@Named("classpath-extractor")
@Singleton
public class ClasspathExtractorParticipant extends AbstractMavenLifecycleParticipant {

  ClasspathExtractorParticipant() {
    LOGGER.info("ClasspathExtractorParticipant initialized");
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathExtractorParticipant.class);

  private static final Set<String> SKIPPED_PLUGIN_ARTIFACT_IDS = new HashSet<>(Arrays.asList(
      "org.apache.maven.plugins:maven-surefire-plugin",
      "org.apache.maven.plugins:maven-enforcer-plugin",
      "org.apache.maven.plugins:maven-checkstyle-plugin",
      "org.apache.maven.plugins:sortpom-maven-plugin",
      "org.jacoco:jacoco-maven-plugin",
      "org.codehaus.mojo:license-maven-plugin",
      "org.apache.maven.plugins:maven-compiler-plugin"));

  private static final Set<String> SKIPPED_PHASES = new HashSet<>(Arrays.asList(
      "initialize",
      "validate",
      "generate-resources",
      "process-resources",
      "generate-test-resources",
      "process-test-resources",
      "test-compile",
      "compile"));

  private static final String SKIP_PHASES_USER_PROPERTY = "skipPhases";
  private static final String INCLUDE_PHASES_USER_PROPERTY = "includePhases";
  private static final String SKIP_PLUGINS_USER_PROPERTY = "skipPlugins";
  private static final String INCLUDE_PLUGINS_USER_PROPERTY = "includePlugins";

  @Override
  public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
    Set<String> skippedPhases = new HashSet<>(getStringListUserProperty(session, SKIP_PHASES_USER_PROPERTY));
    skippedPhases.addAll(SKIPPED_PHASES);
    Set<String> includedPhases = new HashSet<>(getStringListUserProperty(session, INCLUDE_PHASES_USER_PROPERTY));
    skippedPhases.removeAll(includedPhases);

    Set<String> skippedPlugins = new HashSet<>(getStringListUserProperty(session, SKIP_PLUGINS_USER_PROPERTY));
    skippedPlugins.addAll(SKIPPED_PLUGIN_ARTIFACT_IDS);
    Set<String> includedPlugins = new HashSet<>(getStringListUserProperty(session, INCLUDE_PLUGINS_USER_PROPERTY));
    skippedPlugins.removeAll(includedPlugins);

    
    for (MavenProject project : session.getProjects()) {
      Build build = project.getBuild();
      if (build == null || build.getPlugins() == null) {
        continue;
      }

      for (Plugin plugin : build.getPlugins()) {
        if (plugin == null || plugin.getArtifactId() == null) {
          continue;
        }
        String pluginKey = plugin.getGroupId() + ":" + plugin.getArtifactId();
        if (skippedPlugins.contains(pluginKey)) {
          plugin.setExecutions(Collections.emptyList());
          LOGGER.info("Removed execution(s) from plugin {} in project {}", pluginKey, project.getArtifactId());
        } else {
          plugin.setExecutions(plugin.getExecutions().stream().filter(execution -> !skippedPhases.contains(execution.getPhase())).collect(Collectors.toList()));
          LOGGER.info("Removed execution(s) from plugin {} in project {}", pluginKey, project.getArtifactId());
        }
      }
    }
  }

  private Collection<String> getStringListUserProperty(MavenSession session, String propertyName) {
    String userConfiguredStringList = session.getUserProperties().getProperty(propertyName);
    if (userConfiguredStringList == null || userConfiguredStringList.trim().isEmpty()) {
      return Collections.emptyList();
    }
    
    return Arrays.stream(userConfiguredStringList.split(","))
        .filter(phase -> !phase.trim().isEmpty())
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  public void reportMavenTarget(MavenSession session, String id, MavenTarget mavenTarget) {
    LOGGER.info("Reporting maven target {}", id);
    Map<String, MavenTargetInfo> mavenTargets = (Map<String, MavenTargetInfo>) session.getUserProperties().get("mavenTargets");
    Map<String, String> reportedDependencies = (Map<String, String>) session.getUserProperties().get("reportedDependencies");
    for (Map.Entry<String, Dependency> dependency : mavenTarget.getDependencies().entrySet()) {
      reportedDependencies.put(dependency.getKey(), dependency.getValue().getPath());
    }

    List<String> testDependencies = new ArrayList<>();
    List<String> dependencies = new ArrayList<>();
    for (Map.Entry<String, Dependency> dependency : mavenTarget.getDependencies().entrySet()) {
      if (dependency.getValue().getScope().equals("test")) {
        testDependencies.add(dependency.getKey());
      } else {
        dependencies.add(dependency.getKey());
      }
    }

    mavenTargets.put(id,
        new MavenTargetInfo(mavenTarget.getPom(), mavenTarget.getInputFolders(), mavenTarget.getOutputFolder(),
            dependencies, testDependencies, mavenTarget.getTestInputFolders(), mavenTarget.getTestOutputFolder(), mavenTarget.getJdk()));
    LOGGER.info("Maven target {} reported, now have {} maven targets", id, mavenTargets.size());
  }

  @Override
  public void afterSessionStart(MavenSession session) throws MavenExecutionException {
    session.getUserProperties().put("mavenTargets", new TreeMap<String, MavenTargetInfo>());
    session.getUserProperties().put("reportedDependencies", new TreeMap<String, String>());
  }

  @SuppressWarnings("unchecked")
  @Override
  public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
    Map<String, MavenTargetInfo> mavenTargets = (Map<String, MavenTargetInfo>) session.getUserProperties()
        .get("mavenTargets");
    Map<String, String> reportedDependencies = (Map<String, String>) session.getUserProperties()
        .get("reportedDependencies");
    LOGGER.info("Writing classpath to file, {} maven targets", mavenTargets.size());
    String path = session.getUserProperties().getProperty("outfile");
    if (path == null) {
      path = "classpath.json";
      session.getUserProperties().setProperty("outfile", path);
    }

    Gson gson = new Gson();
    try (JsonWriter writer = gson.newJsonWriter(new PrintWriter(
        Files.newBufferedWriter(Paths.get(path), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
      writer.setIndent("  ");
      gson.toJson(new MavenExtractedInfo(mavenTargets, reportedDependencies), MavenExtractedInfo.class, writer);
    } catch (IOException e) {
      throw new MavenExecutionException("Failed to write classpath to file", e);
    }
    LOGGER.info("Classpath written to file");
  }

  @SuppressWarnings("unchecked")
  public File findFileForArtifact(MavenSession session, Artifact artifact) {
    Map<String, MavenTargetInfo> mavenTargets = (Map<String, MavenTargetInfo>) session.getUserProperties()
        .get("mavenTargets");
    String key = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    MavenTargetInfo mavenTarget = mavenTargets.get(key);
    if (mavenTarget != null) {
      return new File(mavenTarget.getOutputFolder());
    }
    try {
      return Files.createTempDirectory("mbt-extractor").toFile();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
}
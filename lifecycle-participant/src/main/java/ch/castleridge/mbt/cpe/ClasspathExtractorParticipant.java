/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import ch.castleridge.mbt.cpe.json.MavenExtractedInfo;
import ch.castleridge.mbt.cpe.json.MavenTargetInfo;
import ch.castleridge.mbt.cpe.json.MavenDependencyInfo;

@Named("classpath-extractor")
@Singleton
public class ClasspathExtractorParticipant extends AbstractMavenLifecycleParticipant {

  ClasspathExtractorParticipant() {
    LOGGER.info("ClasspathExtractorParticipant initialized");
  }

  @Inject()
  private RepositorySystem repositorySystem;

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
  private static final String COMPILER_PLUGIN_KEY = "org.apache.maven.plugins:maven-compiler-plugin";
  private static final String COMPILE_OPTIONS_BY_PROJECT_USER_PROPERTY = "compileOptionsByProject";
  private static final String DEBUG_COMPILE_OPTIONS_BY_PROJECT_USER_PROPERTY = "debugCompileOptionsByProject";
  private static final MavenCompilerOptionsExtractor COMPILER_OPTIONS_EXTRACTOR = new MavenCompilerOptionsExtractor();

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
    Map<String, List<String>> compileOptionsByProject = new TreeMap<>();
    Map<String, List<String>> debugCompileOptionsByProject = new TreeMap<>();

    for (MavenProject project : session.getProjects()) {
      Build build = project.getBuild();
      if (build == null || build.getPlugins() == null) {
        continue;
      }

      String projectId = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();

      for (Plugin plugin : build.getPlugins()) {
        String pluginKey = plugin.getGroupId() + ":" + plugin.getArtifactId();
        if (COMPILER_PLUGIN_KEY.equals(pluginKey)) {
          compileOptionsByProject.put(projectId, extractCompileJavacOptions(plugin));
          debugCompileOptionsByProject.put(projectId, extractDebugCompileJavacOptions(plugin));
        }
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
  public void reportPomFile(MavenSession session, String pomFile) {
    // System.err.println("Reporting pom file: " + pomFile);
    Set<String> pomFilesProcessed = (Set<String>) session.getUserProperties().get("pomFilesProcessed");
    pomFilesProcessed.add(pomFile);
  }

  @SuppressWarnings("unchecked")
  public void reportMavenTarget(MavenSession session, String id, MavenTarget mavenTarget) {

    Map<String, MavenTargetInfo> mavenTargets = (Map<String, MavenTargetInfo>) session.getUserProperties().get("mavenTargets");
    Map<String, MavenDependencyInfo> reportedDependencies = (Map<String, MavenDependencyInfo>) session.getUserProperties().get("reportedDependencies");
    for (Map.Entry<String, Dependency> dependency : mavenTarget.getDependencies().entrySet()) {
      if (!mavenTargets.containsKey(dependency.getKey()) && !reportedDependencies.containsKey(dependency.getKey())) {
        reportedDependencies.put(dependency.getKey(), new MavenDependencyInfo(dependency.getValue().path, resolveSourcesJar(session, dependency.getValue())));
      }
    }

    List<String> testDependencies = new ArrayList<>();
    List<String> dependencies = new ArrayList<>();
    for (Map.Entry<String, Dependency> dependency : mavenTarget.getDependencies().entrySet()) {
      if (dependency.getValue().scope.equals("test")) {
        testDependencies.add(dependency.getKey());
      } else {
        dependencies.add(dependency.getKey());
      }
    }

    mavenTargets.put(id,
        new MavenTargetInfo(mavenTarget.getInputFolders(), mavenTarget.getOutputFolder(),
            dependencies, testDependencies, mavenTarget.getTestInputFolders(), mavenTarget.getTestOutputFolder(),
            mavenTarget.getBuildDirectory(), mavenTarget.getJdk(), getCompileJavacOptions(session, id),
            getTestCompileJavacOptions(session, id)));
    LOGGER.info("Maven target {} reported, now have {} maven targets", id, mavenTargets.size());
  }

  private String resolveSourcesJar(MavenSession session, Dependency dependency) {
    DefaultArtifact sources = new DefaultArtifact(
        dependency.groupId,
        dependency.artifactId,
        "sources",
        "jar",
        dependency.baseVersion);

    ArtifactRequest request = new ArtifactRequest();
    request.setArtifact(sources);

    try {
      ArtifactResult result = repositorySystem.resolveArtifact(session.getRepositorySession(), request);
      return result.getArtifact().getFile().getAbsolutePath();
    } catch (Exception e) {
      LOGGER.warn("Error resolving sources jar for artifact " + dependency.groupId + ":" + dependency.artifactId + ":"
          + dependency.baseVersion);
      return null;
    }
  }

  @Override
  public void afterSessionStart(MavenSession session) {
    session.getUserProperties().put("pomFilesProcessed", Collections.synchronizedSet(new HashSet<>()));
    session.getUserProperties().put("mavenTargets", Collections.synchronizedMap(new TreeMap<String, MavenTargetInfo>()));
    session.getUserProperties().put("reportedDependencies", Collections.synchronizedMap(new TreeMap<String, MavenDependencyInfo>()));
    session.getUserProperties().put(COMPILE_OPTIONS_BY_PROJECT_USER_PROPERTY, Collections.synchronizedMap(new TreeMap<String, List<String>>()));
    session.getUserProperties().put(DEBUG_COMPILE_OPTIONS_BY_PROJECT_USER_PROPERTY, Collections.synchronizedMap(new TreeMap<String, List<String>>()));
  }

  @SuppressWarnings("unchecked")
  @Override
  public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
    Map<String, MavenTargetInfo> mavenTargets = (Map<String, MavenTargetInfo>) session.getUserProperties()
        .get("mavenTargets");
    Map<String, MavenDependencyInfo> reportedDependencies = (Map<String, MavenDependencyInfo>) session.getUserProperties()
        .get("reportedDependencies");
    Set<String> pomFilesProcessed = (Set<String>) session.getUserProperties().get("pomFilesProcessed");
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
      gson.toJson(new MavenExtractedInfo(mavenTargets, reportedDependencies, pomFilesProcessed), MavenExtractedInfo.class, writer);
    } catch (IOException e) {
      throw new MavenExecutionException("Failed to write classpath to file", e);
    }
    LOGGER.info("Classpath written to file");
  }

  @SuppressWarnings("unchecked")
  public File findFileForArtifact(MavenSession session, Artifact artifact) {
    Map<String, MavenTargetInfo> mavenTargets = (Map<String, MavenTargetInfo>) session.getUserProperties()
        .get("mavenTargets");
    String key = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getBaseVersion();
    MavenTargetInfo mavenTarget = mavenTargets.get(key);
    if (mavenTarget != null) {
      return new File(mavenTarget.getOutputFolder());
    }

    return null;
  }

  @SuppressWarnings("unchecked")
  private List<String> getCompileJavacOptions(MavenSession session, String id) {
    Map<String, List<String>> optionsByProject = (Map<String, List<String>>) session
        .getUserProperties()
        .get(COMPILE_OPTIONS_BY_PROJECT_USER_PROPERTY);
    if (optionsByProject == null) {
      return Collections.emptyList();
    }
    List<String> compileOptions = optionsByProject.get(id);
    if (compileOptions == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(compileOptions);
  }

  @SuppressWarnings("unchecked")
  private List<String> getTestCompileJavacOptions(MavenSession session, String id) {
    Map<String, List<String>> optionsByProject = (Map<String, List<String>>) session
        .getUserProperties()
        .get(DEBUG_COMPILE_OPTIONS_BY_PROJECT_USER_PROPERTY);
    if (optionsByProject == null) {
      return Collections.emptyList();
    }
    List<String> testCompileOptions = optionsByProject.get(id);
    if (testCompileOptions == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(testCompileOptions);
  }

  private List<String> extractCompileJavacOptions(Plugin plugin) {
    List<String> options = Collections.emptyList();
    for (PluginExecution execution : plugin.getExecutions()) {
      if (isCompileExecution(execution)) {
        options = extractJavacOptionsFromExecution(execution);
      }
    }
    return options;
  }

  private List<String> extractDebugCompileJavacOptions(Plugin plugin) {
    List<String> options = Collections.emptyList();
    for (PluginExecution execution : plugin.getExecutions()) {
      if (isTestCompileExecution(execution)) {
        options = extractJavacOptionsFromExecution(execution);
      }
    }
    return options;
  }

  private List<String> extractJavacOptionsFromExecution(PluginExecution execution) {
    Object configuration = execution.getConfiguration();
    if (configuration instanceof Xpp3Dom) {
      return COMPILER_OPTIONS_EXTRACTOR.extractFromConfiguration((Xpp3Dom) configuration);
    }
    return Collections.emptyList();
  }

  private boolean isCompileExecution(PluginExecution execution) {
    if (execution.getGoals() != null && execution.getGoals().contains("compile")) {
      return true;
    }
    if ("default-compile".equals(execution.getId())) {
      return true;
    }
    return "compile".equals(execution.getPhase());
  }

  private boolean isTestCompileExecution(PluginExecution execution) {
    if (execution.getGoals() != null && execution.getGoals().contains("testCompile")) {
      return true;
    }
    if ("default-testCompile".equals(execution.getId())) {
      return true;
    }
    return "test-compile".equals(execution.getPhase());
  }
}
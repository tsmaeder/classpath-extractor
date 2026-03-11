package ch.castleridge.mbt.cpe;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named("classpath-extractor")
@Singleton
public class ClasspathExtractorParticipant extends AbstractMavenLifecycleParticipant {
  private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathExtractorParticipant.class);

  private static final Set<String> SKIPPED_PLUGIN_ARTIFACT_IDS = new HashSet<>(Arrays.asList(
    "maven-compiler-plugin",
      "maven-surefire-plugin",
      "maven-enforcer-plugin",
      "maven-checkstyle-plugin",
      "sortpom-maven-plugin",
      "jacoco-maven-plugin",
    "license-maven-plugin"));

  public void doSomething() {
    LOGGER.info("Doing something");
  }

  @Override
  public void afterSessionStart(MavenSession session) throws MavenExecutionException {
    String path = session.getUserProperties().getProperty("outfile");
    if (path == null) {
      path = "classpath.json";
      session.getUserProperties().setProperty("outfile", path);
    }
    try (PrintWriter writer = new PrintWriter(
        Files.newBufferedWriter(Paths.get(path), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      writer.println('{');
    } catch (IOException e) {
      throw new MavenExecutionException("Failed to create classpath output file", e);
    }
  }

  @Override
  public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
    if (session.getProjects() == null || session.getProjects().isEmpty()) {
      return;
    }

    for (MavenProject project : session.getProjects()) {
      Build build = project.getBuild();
      if (build == null || build.getPlugins() == null) {
        continue;
      }

      for (Plugin plugin : build.getPlugins()) {
        if (plugin == null || plugin.getArtifactId() == null) {
          continue;
        }
        if (SKIPPED_PLUGIN_ARTIFACT_IDS.contains(plugin.getArtifactId())) {
          plugin.setExecutions(Collections.emptyList());
          LOGGER.info("Removed execution(s) from plugin {} in project {}",
              plugin.getArtifactId(), project.getArtifactId());
        }
      }
    }
  }

  @Override
  public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
    String path = session.getUserProperties().getProperty("outfile");
    if (path == null) {
      path = "classpath.json";
    }
    try (PrintWriter writer = new PrintWriter(
        Files.newBufferedWriter(Paths.get(path), StandardOpenOption.APPEND))) {
      writer.println('}');
    } catch (IOException e) {
      throw new MavenExecutionException("Failed to close classpath output file", e);
    }
  }
}

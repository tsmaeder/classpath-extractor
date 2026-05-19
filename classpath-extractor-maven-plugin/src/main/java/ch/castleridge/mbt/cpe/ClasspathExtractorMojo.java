/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;

@Mojo(name = "extract", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true, requiresProject = true, defaultPhase = LifecyclePhase.TEST_COMPILE)
public class ClasspathExtractorMojo extends AbstractMojo {

  private static final Set<String> SKIPPED_PACKAGING = Set.of("pom");

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Inject()
  private ToolchainManager toolchainManager;

  @Inject()
  private ClasspathExtractorParticipant participant;

  @Override
  public void execute() {
    getLog().info("Extracting classpath information for project " + project.getGroupId() + ":" + project.getArtifactId()
        + ":" + project.getVersion());

    participant.reportPomFile(session, project.getFile().toPath().toAbsolutePath().normalize().toString());
    if (SKIPPED_PACKAGING.contains(project.getPackaging())) {
      getLog().info("Skipping project " + project.getGroupId() + ":" + project.getArtifactId() + ":"
          + project.getVersion() + " because it has packaging " + project.getPackaging());
      return;
    }
    Map<String, Dependency> dependencies = new HashMap<>();
    for (Artifact dep : project.getArtifacts()) {
      String key = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getBaseVersion();
      dependencies.put(key, new Dependency(dep.getGroupId(), dep.getArtifactId(), dep.getBaseVersion(), dep.getFile().toString(), dep.getScope()));
    }

    MavenTarget mavenTarget = new MavenTarget(
        project.getCompileSourceRoots(),
        project.getBuild().getOutputDirectory(),
        project.getTestCompileSourceRoots(),
        project.getBuild().getTestOutputDirectory(),
        project.getBuild().getDirectory(),
        getJavaHome(),
        dependencies);
    String id = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
    participant.reportMavenTarget(session, id, mavenTarget);
  }

  @SuppressWarnings("deprecation")
  private String getJavaHome() {
    Toolchain tc = toolchainManager.getToolchainFromBuildContext("jdk", session);

    if (tc instanceof DefaultJavaToolChain) {
      return ((DefaultJavaToolChain) tc).getJavaHome();
    }

    if (tc != null) {
      String java = tc.findTool("java");
      if (java != null) {
        return new File(java).getParentFile().getParentFile().getAbsolutePath();
      }
    }

    return null;
  }
}

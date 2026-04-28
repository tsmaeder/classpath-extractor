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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

@Mojo(name = "extract", requiresDependencyResolution = ResolutionScope.TEST, requiresProject = true, defaultPhase = LifecyclePhase.TEST_COMPILE)
public class ClasspathExtractorMojo extends AbstractMojo {

  private static final Set<String> SKIPPED_PACKAGING = Set.of("pom");

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Inject()
  private RepositorySystem repositorySystem;

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
  private RepositorySystemSession repoSession;

  @Inject()
  private ToolchainManager toolchainManager;

  @Inject()
  private ClasspathExtractorParticipant participant;

  private File resolveSourcesJar(Artifact artifact) {
      DefaultArtifact sources = new DefaultArtifact(
          artifact.getGroupId(),
          artifact.getArtifactId(),
          "sources",
          "jar",
          artifact.getVersion()
      );      
  
      ArtifactRequest request = new ArtifactRequest();
      request.setArtifact(sources);
  
      try {
        ArtifactResult result = repositorySystem.resolveArtifact(repoSession, request);
        return result.getArtifact().getFile();
      } catch (Exception e) {
        getLog().warn("Error resolving sources jar for artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion(), e);
        return null;
      }
  }

  @Override
  public void execute() throws MojoExecutionException {
    getLog().info("Extracting classpath information for project " + project.getGroupId() + ":" + project.getArtifactId()
        + ":" + project.getVersion());
    if (SKIPPED_PACKAGING.contains(project.getPackaging())) {
      getLog().info("Skipping project " + project.getGroupId() + ":" + project.getArtifactId() + ":"
          + project.getVersion() + " because it has packaging " + project.getPackaging());
      return;
    }
    Map<String, Dependency> dependencies = new HashMap<>();
    for (Artifact dep : project.getArtifacts()) {
      String key = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getBaseVersion();
      dependencies.put(key, new Dependency(dep.getFile().toString(), dep.getScope(), resolveSourcesJar(dep).getAbsolutePath()));
    }

    MavenTarget mavenTarget = new MavenTarget(project.getFile().toString(),
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

    return System.getProperty("java.home");
  }
}

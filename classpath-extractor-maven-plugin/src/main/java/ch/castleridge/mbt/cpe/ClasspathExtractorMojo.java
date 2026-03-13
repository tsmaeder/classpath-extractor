package ch.castleridge.mbt.cpe;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name = "extract", requiresDependencyResolution = ResolutionScope.TEST

)
public class ClasspathExtractorMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true)
  private MavenSession session;

  @Inject()
  private ClasspathExtractorParticipant participant;

  @Override
  public void execute() throws MojoExecutionException {
    getLog().info("Extracting classpath information for project " + project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion());
      
    Map<String, Dependency> dependencies = new HashMap<>();
    for (Artifact dep : project.getArtifacts()) {
      String key = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();
      dependencies.put(key, new Dependency(dep.getFile().toString(), dep.getScope()));
    }

    MavenTarget mavenTarget = new MavenTarget(project.getFile().toString(),
        project.getCompileSourceRoots(),
        project.getBuild().getOutputDirectory(),
        project.getTestCompileSourceRoots(),
        project.getBuild().getTestOutputDirectory(),
        dependencies);
    String id = project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
    participant.reportMavenTarget(session, id, mavenTarget);

  }
}

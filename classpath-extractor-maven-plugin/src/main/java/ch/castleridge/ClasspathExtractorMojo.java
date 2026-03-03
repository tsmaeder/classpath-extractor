package ch.castleridge;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "extract", defaultPhase = LifecyclePhase.VALIDATE)
public class ClasspathExtractorMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject project;

  @Override
  public void execute() throws MojoExecutionException {
    PropertyWriter jsonWriter = (PropertyWriter) session.getUserProperties().get("jsonWriter");
    jsonWriter.property(
      project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion(),
      writeValue -> {
          dumpProjectInfo(writeValue, project);
      });  
    }

    private void dumpProjectInfo(ValueWriter jsonWriter, MavenProject project) {
      jsonWriter.object(w -> {
          w.property("main", u -> u.array(v -> {
              for (String root : project.getCompileSourceRoots()) {
                  v.element(x -> x.string(root));
              }
          }));
          w.property("test", u -> u.array(v -> {
              for (String root : project.getTestCompileSourceRoots()) {
                  v.element(x -> x.string(root));
              }
          }));
          w.property("dependencies", u -> u.object(v -> {
              for (Artifact dep : project.getArtifacts()) {
                  String key = dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion();
                  v.property(key, x -> x.object(y -> {
                      y.property("path", z -> z.string(dep.getFile().toString()));
                      y.property("scope", z -> z.string(dep.getScope()));
                  }));
              }
          }));
      });
  }
}

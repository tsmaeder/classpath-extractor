package ch.castleridge.mbt.cpe;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

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

@Mojo(name = "extract", defaultPhase = LifecyclePhase.TEST_COMPILE, requiresDependencyResolution = ResolutionScope.TEST)
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
    try {
      participant.doSomething();
    } catch (Exception e) {
      getLog().error("Error doing something", e);
      throw new MojoExecutionException("Error doing something", e);
    }
    
    String outfile = session.getUserProperties().getProperty("outfile");
    if (outfile == null) {
      outfile = "classpath.json";
    }
    Object firstEntry = session.getUserProperties().get("classpathFirstEntry");
    boolean isFirst = firstEntry == null || Boolean.TRUE.equals(firstEntry);

    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
        Paths.get(outfile), StandardOpenOption.APPEND, StandardOpenOption.CREATE))) {
      if (!isFirst) {
        writer.println(",");
      }
      JSONWriter.PropertyWriter jsonWriter = new JSONWriter(writer).object("   ");
      jsonWriter.property(
          project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion(),
          writeValue -> dumpProjectInfo(writeValue, project));
      writer.flush();
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to append to classpath output file", e);
    }

    session.getUserProperties().put("classpathFirstEntry", Boolean.FALSE);
  }

  private void dumpProjectInfo(JSONWriter.ValueWriter jsonWriter, MavenProject project) {
    jsonWriter.object(w -> {
      w.property("pom", u -> u.string(project.getFile().toString()));
      w.property("main", u -> u.array(v -> {
        for (String root : project.getCompileSourceRoots()) {
          v.element(x -> x.string(root));
        }
      }));

      w.property("output", u -> u.string(project.getBuild().getOutputDirectory()));
      w.property("test", u -> u.array(v -> {
        for (String root : project.getTestCompileSourceRoots()) {
          v.element(x -> x.string(root));
        }
      }));
      w.property("testOutput", u -> u.string(project.getBuild().getTestOutputDirectory()));
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

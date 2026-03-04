package ch.castleridge.mbt.cpe;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;

@Named("classpath-extractor")
@Singleton
public class ClasspathExtractorParticipant extends AbstractMavenLifecycleParticipant {

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

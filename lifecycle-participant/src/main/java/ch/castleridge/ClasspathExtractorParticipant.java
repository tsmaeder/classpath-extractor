package ch.castleridge;

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
    Path path = Paths.get(this.cpFile);
    PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
    PropertyWriter jsonWriter = new JSONWriter(writer).object();
    writer.println('{');
    session.getUserProperties().put("jsonWriter", jsonWriter);
    session.getUserProperties().put("writer", writer);
  }

  @Override
  public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
    PrintWriter writer = (PrintWriter) session.getUserProperties().get("writer");
    
    writer.println('}');
    writer.flush();
    writer.close();
  }
}

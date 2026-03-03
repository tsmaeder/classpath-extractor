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
    // Hook: after Maven session starts
  }

  @Override
  public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
    // Hook: after projects are read (can access project list)
  }
}

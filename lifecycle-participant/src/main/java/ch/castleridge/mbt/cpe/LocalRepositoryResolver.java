package ch.castleridge.mbt.cpe;

import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.resolution.ArtifactResolverPostProcessor;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named("local-repository-resolver")
@Singleton
public class LocalRepositoryResolver implements ArtifactResolverPostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalRepositoryResolver.class);

    @Inject
    private ClasspathExtractorParticipant participant;

    @Inject
    private MavenSession session;

    @Override
    public void postProcess(RepositorySystemSession session, List<ArtifactResult> results) {
        for (ArtifactResult result : results) {
            // Check if the result failed (or if you want to override a successful one)
            if (!result.isResolved()) {
                LOGGER.info("Artifact {} not resolved", result.getRequest().getArtifact());
                File f = participant.findFileForArtifact(this.session, result.getRequest().getArtifact());
                
                result.setArtifact(result.getRequest().getArtifact().setFile(f));
            }
        }
    }
}
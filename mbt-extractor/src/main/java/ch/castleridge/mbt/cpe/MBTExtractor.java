package ch.castleridge.mbt.cpe;

import com.google.gson.Gson;

import ch.castleridge.mbt.cpe.json.MavenExtractedInfo;
import ch.castleridge.mbt.cpe.json.MavenTargetInfo;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MBTExtractor {

  private static final String DEFAULT_LIFECYCLE_PARTICIPANT =
      "lifecycle-participant/target/lifecycle-participant-1.0-SNAPSHOT.jar";
  private static final String LIFECYCLE_PARTICIPANT_PROPERTY = "mbt.lifecycleParticipant";

  public static void main(String[] args) {
    try {
      String lifecycleParticipant = getLifecycleParticipantPath(args);
      Path baseDir = Path.of(".").toAbsolutePath().normalize();
      Path participantJar = baseDir.resolve(lifecycleParticipant).normalize().toAbsolutePath();
      if (!Files.isRegularFile(participantJar)) {
        System.err.println("Lifecycle participant JAR not found: " + participantJar);
        System.exit(1);
      }

      List<Path> todo = findPomFilesSortedByPathLength(baseDir);
      if (todo.isEmpty()) {
        System.out.println("No pom.xml files found.");
        return;
      }

      Gson gson = new Gson();

      while (!todo.isEmpty()) {
        Path pomPath = todo.remove(0);
        Path projectDir = pomPath.getParent();
        if (projectDir == null) {
          continue;
        }

        Path outfile = createUniqueOutfile();
        try {
          MavenBuildRunner.runBuild(projectDir, participantJar, outfile);
          MavenExtractedInfo extractedInfo = readClasspathJson(gson, outfile);
          List<Path> pomPathsFromJson = collectPomPaths(extractedInfo);
          removePomsFromTodo(todo, pomPathsFromJson);
        } finally {
          try {
            Files.deleteIfExists(outfile);
          } catch (IOException ignored) {
            // best-effort cleanup
          }
        }
      }

      System.out.println("MBT extraction complete.");
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static String getLifecycleParticipantPath(String[] args) {
    String fromProperty = System.getProperty(LIFECYCLE_PARTICIPANT_PROPERTY);
    if (fromProperty != null && !fromProperty.isEmpty()) {
      return fromProperty;
    }
    for (int i = 0; i < args.length - 1; i++) {
      if ("--lifecycle-participant".equals(args[i])) {
        return args[i + 1];
      }
    }
    return DEFAULT_LIFECYCLE_PARTICIPANT;
  }

  private static List<Path> findPomFilesSortedByPathLength(Path baseDir) throws IOException {
    List<Path> poms = new ArrayList<>();
    try (var stream = Files.find(baseDir, Integer.MAX_VALUE,
        (p, attrs) -> attrs.isRegularFile() && p.getFileName().toString().equals("pom.xml"))) {
      stream.forEach(poms::add);
    }
    poms.sort((a, b) -> Integer.compare(a.toString().length(), b.toString().length()));
    return poms;
  }

  private static Path createUniqueOutfile() throws IOException {
    return Files.createTempFile("classpath", ".json");
  }

  private static MavenExtractedInfo readClasspathJson(Gson gson, Path outfile)
      throws IOException {
    try (Reader reader = Files.newBufferedReader(outfile)) {
      return gson.fromJson(reader, MavenExtractedInfo.class);
    }
  }

  private static List<Path> collectPomPaths(MavenExtractedInfo extractedInfo) {
    List<Path> result = new ArrayList<>();
    if (extractedInfo == null) {
      return result;
    }
    for (MavenTargetInfo target : extractedInfo.mavenTargets.values()) {
      if (target != null && target.getPom() != null && !target.getPom().isEmpty()) {
        result.add(Path.of(target.getPom()).normalize().toAbsolutePath());
      }
    }
    return result;
  }

  private static void removePomsFromTodo(List<Path> todo, List<Path> pomPathsFromJson) {
    if (pomPathsFromJson == null || pomPathsFromJson.isEmpty()) {
      return;
    }
    todo.removeIf(todoPath -> {
      Path normalized = todoPath.normalize().toAbsolutePath();
      return pomPathsFromJson.stream().anyMatch(normalized::equals);
    });
  }
}

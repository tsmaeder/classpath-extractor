package ch.castleridge.mbt.cpe;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import ch.castleridge.mbt.cpe.json.MBTDependencyModuleInfo;
import ch.castleridge.mbt.cpe.json.MBTInfo;
import ch.castleridge.mbt.cpe.json.MBTTargetInfo;
import ch.castleridge.mbt.cpe.json.MavenExtractedInfo;
import ch.castleridge.mbt.cpe.json.MavenTargetInfo;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MBTExtractor {

  public static void main(String[] args) {
    try {

      Map<String, MBTTargetInfo> targets = new TreeMap<>();
      Map<String, MBTDependencyModuleInfo> dependencyModules = new TreeMap<>();

      Path baseDir = Path.of(".").toAbsolutePath().normalize();

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
          MavenBuildRunner.runBuild(projectDir, outfile);
          MavenExtractedInfo extractedInfo = readClasspathJson(gson, outfile);
          if (extractedInfo != null) {
            System.out.println("Extracted info for: " + extractedInfo.mavenTargets.size() + " maven targets");
            List<Path> pomPathsFromJson = collectPomPaths(extractedInfo);
            removePomsFromTodo(todo, pomPathsFromJson);
            extractTargets(targets, extractedInfo);
            dependencyModules.putAll(mapDependencyModules(extractedInfo.reportedDependencies));
          } else {
            System.err.println("No extracted info for: " + projectDir);
          }
        } finally {
          try {
            Files.deleteIfExists(outfile);
          } catch (IOException ignored) {
            // best-effort cleanup
          }
        }
      }

      MBTInfo mbtInfo = new MBTInfo(targets.values(), dependencyModules.values());
            try (JsonWriter writer = gson.newJsonWriter(Files.newBufferedWriter(Path.of("mbt.json"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
              writer.setIndent("  ");
              gson.toJson(mbtInfo, MBTInfo.class, writer);
            } catch (IOException e) {
              System.err.println("Failed to write mbt.json to file: " + e.getMessage());
            }

      System.out.println("MBT extraction complete.");
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static Map<String, MBTDependencyModuleInfo> mapDependencyModules(Map<String,String> reportedDependencies) {
    Map<String, MBTDependencyModuleInfo> result = new HashMap<>();
    for (Map.Entry<String,String> entry : reportedDependencies.entrySet()) {
      MBTDependencyModuleInfo dependencyModule = new MBTDependencyModuleInfo();
      dependencyModule.id = entry.getKey();
      dependencyModule.path = entry.getValue();
      result.put(entry.getKey(), dependencyModule);
    }
    return result;
  }

  private static void extractTargets(Map<String, MBTTargetInfo> targets, MavenExtractedInfo extractedInfo) {
    for (Map.Entry<String, MavenTargetInfo> entry : extractedInfo.mavenTargets.entrySet()) {
      String id = entry.getKey();
      String testId = id + ":test";
      MavenTargetInfo target = entry.getValue();
      MBTTargetInfo mbtTarget = new MBTTargetInfo();
      mbtTarget.id = id;
      mbtTarget.sources = target.getInputFolders();
      List<String> dependencies = new ArrayList<>(target.getDependencies());
      mbtTarget.dependencyModules = dependencies;
      targets.put(id, mbtTarget);
      
      MBTTargetInfo testTarget = new MBTTargetInfo();
      testTarget.id = testId;
      testTarget.sources = target.getTestInputFolders();

      List<String> testDependencies = new ArrayList<>(target.getDependencies());
      testDependencies.addAll(target.getTestDependencies());
      testTarget.dependencyModules = testDependencies;
      targets.put(testId, testTarget);
    }
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

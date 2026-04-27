/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import ch.castleridge.mbt.cpe.json.MBTDependencyModuleInfo;
import ch.castleridge.mbt.cpe.json.MBTInfo;
import ch.castleridge.mbt.cpe.json.MBTTargetInfo;
import ch.castleridge.mbt.cpe.json.MavenExtractedInfo;
import ch.castleridge.mbt.cpe.json.MavenTargetInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class MBTExtractor {
  private static final String LIFECYCLE_PARTICIPANT = "lifecycle-participant-1.0-SNAPSHOT.jar";
  private static final String CLASSPATH_EXTRACTOR_MAVEN_PLUGIN = "classpath-extractor-maven-plugin-1.0-SNAPSHOT.jar";
  private static final String APP_DATA_DIR_NAME = "mbt-extractor";

  private static final String[] PROPERTY_PREFIXES = {
    "skip-phases=",
    "include-phases=",
    "skip-plugins=",
    "include-plugins=",
  };

  private final List<String> extraArguments;
  private final Gson gson = new Gson();
  private final Map<String, MBTTargetInfo> targets = new TreeMap<>();
  private final Map<String, MBTDependencyModuleInfo> dependencyModules = new TreeMap<>();
  private final Set<Path> mavenBuildOutputRoots = new HashSet<>();

  private Path baseDir;
  private List<Path> todo;
  private String profileFile;

  public MBTExtractor(String[] args) {
    this.extraArguments = parseExtraArguments(args);
  }

  public static Path resolveJar(String jarName) throws IOException {
    Path appDataDir = getAppDataDirectory();
    Files.createDirectories(appDataDir);
    Path participantJar = appDataDir.resolve(jarName).toAbsolutePath();

    boolean shouldReplace = jarName.contains("SNAPSHOT");
    if (shouldReplace || !Files.isRegularFile(participantJar)) {
      copyFromResources(jarName, participantJar);
    }

    return participantJar;
  }

  private static void copyFromResources(String jarName, Path targetJar) throws IOException {
    try (InputStream in = MavenBuildRunner.class.getClassLoader().getResourceAsStream(jarName)) {
      if (in == null) {
        throw new IllegalArgumentException(
            "Lifecycle participant JAR not found in application resources: " + jarName);
      }
      Files.copy(in, targetJar, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Path getAppDataDirectory() {
    String os = System.getProperty("os.name", "").toLowerCase();
    String userHome = System.getProperty("user.home", "");

    if (os.contains("win")) {
      String localAppData = System.getenv("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isEmpty()) {
        return Path.of(localAppData, APP_DATA_DIR_NAME);
      }
      String appData = System.getenv("APPDATA");
      if (appData != null && !appData.isEmpty()) {
        return Path.of(appData, APP_DATA_DIR_NAME);
      }
      return Path.of(userHome, "AppData", "Local", APP_DATA_DIR_NAME);
    }
    if (os.contains("mac")) {
      return Path.of(userHome, "Library", "Application Support", APP_DATA_DIR_NAME);
    }

    String xdgDataHome = System.getenv("XDG_DATA_HOME");
    if (xdgDataHome != null && !xdgDataHome.isEmpty()) {
      return Path.of(xdgDataHome, APP_DATA_DIR_NAME);
    }
    return Path.of(userHome, ".local", "share", APP_DATA_DIR_NAME);
  }

  public static void main(String[] args) throws Exception {
    new MBTExtractor(args).run();
  }

  private void run() throws Exception {
    baseDir = Path.of(".").toAbsolutePath().normalize();

    todo = findPomFilesSortedByPathLength();
    if (todo.isEmpty()) {
      System.out.println("No pom.xml files found.");
      return;
    }

    MavenBuildRunner.runMaven(baseDir, null, (List<String> command) -> {
        command.add("install:install-file");
        command.add("-Dfile=" + resolveJar(CLASSPATH_EXTRACTOR_MAVEN_PLUGIN).toAbsolutePath());
        command.add("-DgroupId=ch.castleridge");
        command.add("-DartifactId=classpath-extractor-maven-plugin");
        command.add("-Dversion=1.0-SNAPSHOT");
        command.add("-Dpackaging=maven-plugin");
    });

    while (!todo.isEmpty()) {
      Path pomPath = todo.remove(0);
      Path pomAbsolute = pomPath.normalize().toAbsolutePath();
      if (isUnderAnyMavenBuildOutputRoot(pomAbsolute)) {
        continue;
      }
      Path projectDir = pomAbsolute.getParent();
      if (projectDir == null) {
        continue;
      }

      Path outfile = createUniqueOutfile();
      try {
        MavenBuildRunner.runMaven(projectDir, profileFile, (List<String> command) -> {
          command.add("-Dmaven.ext.class.path=" + resolveJar(LIFECYCLE_PARTICIPANT).toAbsolutePath());
          command.add("-Doutfile=" + outfile.toAbsolutePath());
          command.addAll(extraArguments);
          command.add("--fail-never");
          command.add("ch.castleridge:classpath-extractor-maven-plugin:extract");
          command.add("test-compile");
          command.add("ch.castleridge:classpath-extractor-maven-plugin:extract");
        });
        MavenExtractedInfo extractedInfo = readClasspathJson(outfile);
        if (extractedInfo != null) {
          System.out.println("Extracted info for: " + extractedInfo.mavenTargets.size() + " maven targets");
          List<Path> pomPathsFromJson = collectPomPaths(extractedInfo);
          removePomsFromTodo(pomPathsFromJson);
          registerMavenBuildOutputRoots(extractedInfo);
          extractTargets(extractedInfo);
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
  }

  private Map<String, MBTDependencyModuleInfo> mapDependencyModules(Map<String, String> reportedDependencies) {
    Map<String, MBTDependencyModuleInfo> result = new HashMap<>();
    for (Map.Entry<String, String> entry : reportedDependencies.entrySet()) {
      MBTDependencyModuleInfo dependencyModule = new MBTDependencyModuleInfo();
      dependencyModule.id = entry.getKey();
      dependencyModule.path = entry.getValue();
      result.put(entry.getKey(), dependencyModule);
    }
    return result;
  }

  private void extractTargets(MavenExtractedInfo extractedInfo) {
    for (Map.Entry<String, MavenTargetInfo> entry : extractedInfo.mavenTargets.entrySet()) {
      String id = entry.getKey();
      String testId = id + ":test";
      MavenTargetInfo target = entry.getValue();
      MBTTargetInfo mbtTarget = new MBTTargetInfo();
      mbtTarget.id = id;
      mbtTarget.javacOptions = target.getCompileJavacOptions();
      mbtTarget.sources = target.getInputFolders();
      mbtTarget.classes = List.of(target.getOutputFolder());
      mbtTarget.jdk = target.getJdk();
      List<String> dependencies = new ArrayList<>(target.getDependencies());
      mbtTarget.dependencyModules = dependencies;
      targets.put(id, mbtTarget);

      MBTTargetInfo testTarget = new MBTTargetInfo();
      testTarget.id = testId;
      testTarget.javacOptions = target.getTestCompileJavacOptions();
      testTarget.sources = target.getTestInputFolders();
      testTarget.classes = List.of(target.getTestOutputFolder());
      testTarget.jdk = target.getJdk();
      List<String> testDependencies = new ArrayList<>(target.getDependencies());
      testDependencies.addAll(target.getTestDependencies());
      testTarget.dependencyModules = testDependencies;
      targets.put(testId, testTarget);
    }
  }

  private List<Path> findPomFilesSortedByPathLength() throws IOException {
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

  private MavenExtractedInfo readClasspathJson(Path outfile) throws IOException {
    try (Reader reader = Files.newBufferedReader(outfile)) {
      return gson.fromJson(reader, MavenExtractedInfo.class);
    }
  }

  private List<Path> collectPomPaths(MavenExtractedInfo extractedInfo) {
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

  private void removePomsFromTodo(List<Path> pomPathsFromJson) {
    if (pomPathsFromJson == null || pomPathsFromJson.isEmpty()) {
      return;
    }
    todo.removeIf(todoPath -> {
      Path normalized = todoPath.normalize().toAbsolutePath();
      return pomPathsFromJson.stream().anyMatch(normalized::equals);
    });
  }

  /**
   * Registers Maven {@code build.directory} roots (parent of compile/test output dirs) for each
   * reactor project so {@code pom.xml} files unpacked under {@code target/} are skipped later.
   */
  private void registerMavenBuildOutputRoots(MavenExtractedInfo extractedInfo) {
    this.mavenBuildOutputRoots.addAll(extractedInfo.mavenTargets.values().stream().map(MavenTargetInfo::getBuildDirectory).map(Path::of).collect(Collectors.toSet()));
  }

  private boolean isUnderAnyMavenBuildOutputRoot(Path pomFileAbsolute) {
    for (Path root : mavenBuildOutputRoots) {
      if (pomFileAbsolute.startsWith(root)) {
        return true;
      }
    }
    return false;
  }

  private List<String> parseExtraArguments(String[] args) {
    List<String> result = new ArrayList<>();

    for (String arg : args) {
      for (String propertyPrefix : PROPERTY_PREFIXES) {
        if (arg.startsWith("--" + propertyPrefix + "=")) {
          result.add("-D" + propertyPrefix + "=" + arg.substring(propertyPrefix.length() + 3));
          break;
        }
      }
      if (arg.startsWith("--profile=")) {
        profileFile = arg.substring("--profile=".length());
        if (profileFile.isBlank()) {
          profileFile="mbt.jfr";
        }
      } else if (arg.equals("--profile")) {
        profileFile="mbt.jfr";
      }
    }
    return result;
  }
}

package ch.castleridge.mbt.cpe;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;

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
      Type type = TypeToken.getParameterized(Map.class, String.class, ClasspathModule.class).getType();

      while (!todo.isEmpty()) {
        Path pomPath = todo.remove(0);
        Path projectDir = pomPath.getParent();
        if (projectDir == null) {
          continue;
        }

        Path outfile = createUniqueOutfile();
        try {
          runMavenBuild(projectDir, participantJar, outfile);
          Map<String, ClasspathModule> classpath = readClasspathJson(gson, type, outfile);
          List<Path> pomPathsFromJson = collectPomPaths(classpath);
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

  private static void runMavenBuild(Path projectDir, Path lifecycleParticipantJar, Path outfile)
      throws Exception {
    boolean useWrapper = false;
    Path mvnw = projectDir.resolve("mvnw");
    Path mvnwCmd = projectDir.resolve("mvnw.cmd");
    boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    if (windows && Files.isRegularFile(mvnwCmd)) {
      useWrapper = true;
    } else if (Files.isRegularFile(mvnw)) {
      useWrapper = true;
    }

    if (useWrapper) {
      runMavenWithWrapper(projectDir, lifecycleParticipantJar, outfile, windows);
    } else {
      runMavenWithInvoker(projectDir, lifecycleParticipantJar, outfile);
    }
  }

  private static void runMavenWithWrapper(Path projectDir, Path lifecycleParticipantJar,
      Path outfile, boolean windows) throws IOException, InterruptedException {
    String outfileAbs = outfile.toAbsolutePath().toString();
    String jarAbs = lifecycleParticipantJar.toAbsolutePath().toString();
    List<String> command = new ArrayList<>();
    if (windows) {
      command.add(projectDir.resolve("mvnw.cmd").toAbsolutePath().toString());
    } else {
      command.add(projectDir.resolve("mvnw").toAbsolutePath().toString());
    }
    command.add("-Dmaven.ext.class.path=" + jarAbs);
    command.add("-Doutfile=" + outfileAbs);
    command.add("test-compile");
    command.add("ch.castleridge:classpath-extractor-maven-plugin:extract");

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(projectDir.toFile());
    pb.inheritIO();
    Process process = pb.start();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new RuntimeException("Maven build failed with exit code " + exitCode);
    }
  }

  private static void runMavenWithInvoker(Path projectDir, Path lifecycleParticipantJar,
      Path outfile) throws Exception {
    InvocationRequest request = new DefaultInvocationRequest();
    request.setBaseDirectory(projectDir.toFile());
    request.setPomFile(projectDir.resolve("pom.xml").toFile());
    request.addArg("-Dmaven.ext.class.path=" + lifecycleParticipantJar.toAbsolutePath());
    request.addArg("-Doutfile=" + outfile.toAbsolutePath());
    request.setGoals(Arrays.asList("test-compile", "ch.castleridge:classpath-extractor-maven-plugin:extract"));

    Invoker invoker = new DefaultInvoker();
    String mavenHome = System.getenv("MAVEN_HOME");
    if (mavenHome != null && !mavenHome.isEmpty()) {
      invoker.setMavenHome(new File(mavenHome));
    }
    InvocationResult result = invoker.execute(request);
    Integer exitCode = result.getExitCode();
    if (exitCode != null && exitCode.intValue() != 0) {
      throw new RuntimeException("Maven build failed with exit code " + exitCode);
    }
    if (result.getExecutionException() != null) {
      throw result.getExecutionException();
    }
  }

  private static Map<String, ClasspathModule> readClasspathJson(Gson gson, Type type, Path outfile)
      throws IOException {
    try (Reader reader = Files.newBufferedReader(outfile)) {
      return gson.fromJson(reader, type);
    }
  }

  private static List<Path> collectPomPaths(Map<String, ClasspathModule> classpath) {
    List<Path> result = new ArrayList<>();
    if (classpath == null) {
      return result;
    }
    for (ClasspathModule module : classpath.values()) {
      if (module != null && module.getPom() != null && !module.getPom().isEmpty()) {
        result.add(Path.of(module.getPom()).normalize().toAbsolutePath());
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

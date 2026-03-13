package ch.castleridge.mbt.cpe;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;

public final class MavenBuildRunner {

  private static final String DEFAULT_LIFECYCLE_PARTICIPANT =
      "lifecycle-participant-1.0-SNAPSHOT.jar";
  private static final String APP_DATA_DIR_NAME = "mbt-extractor";

  private MavenBuildRunner() {
  }

  public static Path resolveLifecycleParticipantJar() throws IOException {
    String jarName = Path.of(DEFAULT_LIFECYCLE_PARTICIPANT).getFileName().toString();
    if (jarName.isEmpty()) {
      throw new IllegalArgumentException("Invalid lifecycle participant JAR name: " + DEFAULT_LIFECYCLE_PARTICIPANT);
    }

    Path appDataDir = getAppDataDirectory();
    Files.createDirectories(appDataDir);
    Path participantJar = appDataDir.resolve(jarName).toAbsolutePath();

    boolean shouldReplace = jarName.contains("SNAPSHOT");
    if (shouldReplace || !Files.isRegularFile(participantJar)) {
      copyFromResources(jarName, participantJar);
    }

    return participantJar;
  }

  public static void runBuild(Path projectDir, Path outfile)
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
      runWithWrapper(projectDir, outfile, windows);
    } else {
      runWithInvoker(projectDir, outfile);
    }
  }

  private static void runWithWrapper(Path projectDir,
      Path outfile, boolean windows) throws IOException, InterruptedException {
    String outfileAbs = outfile.toAbsolutePath().toString();
    String jarAbs = resolveLifecycleParticipantJar().toAbsolutePath().toString();
    List<String> command = new ArrayList<>();
    if (windows) {
      command.add(projectDir.resolve("mvnw.cmd").toAbsolutePath().toString());
    } else {
      command.add(projectDir.resolve("mvnw").toAbsolutePath().toString());
    }
    command.add("-Dmaven.ext.class.path=" + jarAbs);
    command.add("-Doutfile=" + outfileAbs);
    command.add("--fail-never");
    command.add("ch.castleridge:classpath-extractor-maven-plugin:extract");
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

  private static void runWithInvoker(Path projectDir,
      Path outfile) throws Exception {
    InvocationRequest request = new DefaultInvocationRequest();
    request.setBaseDirectory(projectDir.toFile());
    request.setPomFile(projectDir.resolve("pom.xml").toFile());
    request.addArg("-Dmaven.ext.class.path=" + resolveLifecycleParticipantJar().toAbsolutePath());
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
}

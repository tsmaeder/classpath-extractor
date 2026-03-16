package ch.castleridge.mbt.cpe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class MavenBuildRunner {

  private static final String DEFAULT_LIFECYCLE_PARTICIPANT = "lifecycle-participant-1.0-SNAPSHOT.jar";
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
    Path mvnw = projectDir.resolve("mvnw");
    Path mvnwCmd = projectDir.resolve("mvnw.cmd");
    boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    
    List<String> command = new ArrayList<>();
    if (windows) {
      command.add("cmd.exe");
      command.add("/c");
    }
    
    if (windows) {
      if (Files.isRegularFile(mvnwCmd)) {
        command.add(mvnwCmd.toAbsolutePath().toString());
      } else {
        command.add("mvn");
      }
    } else {
      if (Files.isRegularFile(mvnw)) {
        command.add(mvnw.toAbsolutePath().toString());
      } else {
        command.add("mvn");
      }
    }

    command.add("-Dmaven.ext.class.path=" + resolveLifecycleParticipantJar().toAbsolutePath());
    command.add("-Doutfile=" + outfile.toAbsolutePath());
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
      System.err.println("Maven build failed with exit code " + exitCode);
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

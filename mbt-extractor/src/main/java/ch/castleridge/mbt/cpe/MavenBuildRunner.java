/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MavenBuildRunner {
  public static interface InnerMavenBuildRunner {
    void runMavenCommand(List<String> command) throws Exception;
  }
  private MavenBuildRunner() {
  }

  public static void runMaven(Path projectDir, InnerMavenBuildRunner mavenBuildRunner)
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

    mavenBuildRunner.runMavenCommand(command);

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(projectDir.toFile());
    pb.inheritIO();
    Process process = pb.start();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      System.err.println("Maven build failed with exit code " + exitCode);
    }
  }
}

package ch.castleridge.mbt.cpe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
      "lifecycle-participant/target/lifecycle-participant-1.0-SNAPSHOT.jar";
  private static final String LIFECYCLE_PARTICIPANT_PROPERTY = "mbt.lifecycleParticipant";

  private MavenBuildRunner() {
  }

  public static Path resolveLifecycleParticipantJar(Path baseDir, String[] args) {
    String lifecycleParticipant = getLifecycleParticipantPath(args);
    Path participantJar = baseDir.resolve(lifecycleParticipant).normalize().toAbsolutePath();
    if (!Files.isRegularFile(participantJar)) {
      throw new IllegalArgumentException("Lifecycle participant JAR not found: " + participantJar);
    }
    return participantJar;
  }

  public static void runBuild(Path projectDir, Path lifecycleParticipantJar, Path outfile)
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
      runWithWrapper(projectDir, lifecycleParticipantJar, outfile, windows);
    } else {
      runWithInvoker(projectDir, lifecycleParticipantJar, outfile);
    }
  }

  private static void runWithWrapper(Path projectDir, Path lifecycleParticipantJar,
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

  private static void runWithInvoker(Path projectDir, Path lifecycleParticipantJar,
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
}

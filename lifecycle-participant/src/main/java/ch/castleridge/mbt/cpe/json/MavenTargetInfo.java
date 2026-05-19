/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe.json;

import java.util.List;

public class MavenTargetInfo {
  public MavenTargetInfo(
      List<String> inputFolders,
      String outputFolder,
      List<String> dependencies,
      List<String> testDependencies,
      List<String> testInputFolders,
      String testOutputFolder,
      String buildDirectory,
      String jdk,
      List<String> compileJavacOptions,
      List<String> testCompileJavacOptions) {
    this.inputFolders = inputFolders;
    this.outputFolder = outputFolder;
    this.dependencies = dependencies;
    this.testDependencies = testDependencies;
    this.testInputFolders = testInputFolders;
    this.testOutputFolder = testOutputFolder;
    this.buildDirectory = buildDirectory;
    this.jdk = jdk;
    this.compileJavacOptions = compileJavacOptions;
    this.testCompileJavacOptions = testCompileJavacOptions;
  }

  private List<String> inputFolders;
  private String outputFolder;
  private List<String> dependencies;
  private List<String> testDependencies;
  private List<String> testInputFolders;
  private String testOutputFolder;
  private String jdk;
  private List<String> compileJavacOptions;
  private List<String> testCompileJavacOptions;
  private String buildDirectory;

  public List<String> getInputFolders() {
    return inputFolders;
  }

  public String getOutputFolder() {
    return outputFolder;
  }

  public List<String> getDependencies() {
    return dependencies;
  }

  public List<String> getTestDependencies() {
    return testDependencies;
  }

  public List<String> getTestInputFolders() {
    return testInputFolders;
  }

  public String getTestOutputFolder() {
    return testOutputFolder;
  }

  public String getJdk() {
    return jdk;
  }

  public List<String> getCompileJavacOptions() {
    return compileJavacOptions;
  }

  public List<String> getTestCompileJavacOptions() {
    return testCompileJavacOptions;
  }

  public String getBuildDirectory() {
    return buildDirectory;
  }
}

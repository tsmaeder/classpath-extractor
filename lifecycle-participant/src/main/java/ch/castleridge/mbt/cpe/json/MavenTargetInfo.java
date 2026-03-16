package ch.castleridge.mbt.cpe.json;

import java.util.List;

public class MavenTargetInfo {
  public MavenTargetInfo(String pom,
      List<String> inputFolders,
      String outputFolder,
      List<String> dependencies,
      List<String> testDependencies,
      List<String> testInputFolders,
      String testOutputFolder) {
    this.pom = pom;
    this.inputFolders = inputFolders;
    this.outputFolder = outputFolder;
    this.dependencies = dependencies;
    this.testDependencies = testDependencies;
    this.testInputFolders = testInputFolders;
    this.testOutputFolder = testOutputFolder;
  }

  private String pom;
  private List<String> inputFolders;
  private String outputFolder;
  private List<String> dependencies;
  private List<String> testDependencies;
  private List<String> testInputFolders;
  private String testOutputFolder;

  public String getPom() {
    return pom;
  }

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
}

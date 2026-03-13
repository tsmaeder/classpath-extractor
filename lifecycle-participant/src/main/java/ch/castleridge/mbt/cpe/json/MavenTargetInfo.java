package ch.castleridge.mbt.cpe.json;

import java.util.List;
import java.util.Set;

public class MavenTargetInfo {
  public MavenTargetInfo(String pom,
      List<String> inputFolders,
      String outputFolder,
      Set<String> dependencies,
      Set<String> testDependencies,
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
  private Set<String> dependencies;
  private Set<String> testDependencies;
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

  public Set<String> getDependencies() {
    return dependencies;
  }

  public Set<String> getTestDependencies() {
    return testDependencies;
  }

  public List<String> getTestInputFolders() {
    return testInputFolders;
  }

  public String getTestOutputFolder() {
    return testOutputFolder;
  }
}

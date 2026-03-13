package ch.castleridge.mbt.cpe;

import java.util.List;
import java.util.Map;

public class MavenTarget {
    private String pom;
    private List<String> inputFolders;
    private String outputFolder;
    private List<String> testInputFolders;
    private String testOutputFolder;
    private java.util.Map<String, Dependency> dependencies;
    

    public MavenTarget(
        String pom,
        List<String> inputFolders,
        String outputFolder,
        List<String> testInputFolders,
        String testOutputFolder,
        java.util.Map<String, Dependency> dependencies
    ) {
        this.pom = pom;
        this.inputFolders = inputFolders;
        this.outputFolder = outputFolder;
        this.dependencies = dependencies;
        this.testInputFolders = testInputFolders;
        this.testOutputFolder = testOutputFolder;
    }

    public String getPom() {
        return pom;
    }
    public List<String> getInputFolders() {
        return inputFolders;
    }

    public String getOutputFolder() {
        return outputFolder;
    }

    public Map<String, Dependency> getDependencies() {
        return dependencies;
    }

    public List<String> getTestInputFolders() {
        return testInputFolders;
    }

    public String getTestOutputFolder() {
        return testOutputFolder;
    }
}
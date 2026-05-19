/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe;

import java.util.List;
import java.util.Map;

public class MavenTarget {
    private List<String> inputFolders;
    private String outputFolder;
    private List<String> testInputFolders;
    private String testOutputFolder;
    private java.util.Map<String, Dependency> dependencies;
    private String buildDirectory;
    private String jdk;

    public MavenTarget(
        List<String> inputFolders,
        String outputFolder,
        List<String> testInputFolders,
        String testOutputFolder,
        String buildDirectory,
        String jdk,
        java.util.Map<String, Dependency> dependencies
    ) {
        this.inputFolders = inputFolders;
        this.outputFolder = outputFolder;
        this.buildDirectory = buildDirectory;
        this.dependencies = dependencies;
        this.jdk = jdk;
        this.testInputFolders = testInputFolders;
        this.testOutputFolder = testOutputFolder;
    }

    public List<String> getInputFolders() {
        return inputFolders;
    }

    public String getOutputFolder() {
        return outputFolder;
    }

    public String getBuildDirectory() {
        return buildDirectory;
    }

    public String getJdk() {
        return jdk;
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
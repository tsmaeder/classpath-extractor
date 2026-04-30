/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe;

// Inner class Dependency as placeholder
public class Dependency {
    public String groupId;
    public String artifactId;
    public String baseVersion;
    public String scope;
    public String path;
    public Dependency(String groupId, String artifactId, String baseVersion, String path, String scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.baseVersion = baseVersion;
        this.scope = scope;
        this.path = path;
    }

    @Override
    public String toString() {
        return "Dependency{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", baseVersion='" + baseVersion + '\'' +
                ", scope='" + scope + '\'' +
                '}';
    }
}
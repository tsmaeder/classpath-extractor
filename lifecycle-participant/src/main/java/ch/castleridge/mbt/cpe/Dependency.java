/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe;

// Inner class Dependency as placeholder
public class Dependency {
    private String path;
    private String scope;

    public Dependency(String path, String scope) {
        this.path = path;
        this.scope = scope;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    @Override
    public String toString() {
        return "Dependency{" +
                "path='" + path + '\'' +
                ", scope='" + scope + '\'' +
                '}';
    }
}
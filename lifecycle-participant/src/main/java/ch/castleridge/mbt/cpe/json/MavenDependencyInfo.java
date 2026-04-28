package ch.castleridge.mbt.cpe.json;

public class MavenDependencyInfo {
    public String path;
    public String sourcesJarPath;

    public MavenDependencyInfo(String path, String sourcesJarPath) {
        this.path = path;
        this.sourcesJarPath = sourcesJarPath;
    }
}

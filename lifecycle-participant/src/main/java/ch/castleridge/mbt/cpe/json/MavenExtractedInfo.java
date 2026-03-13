package ch.castleridge.mbt.cpe.json;

import java.util.Map;

public class MavenExtractedInfo {
  public MavenExtractedInfo(Map<String, MavenTargetInfo> mavenTargets, Map<String, String> reportedDependencies) {
    this.mavenTargets = mavenTargets;
    this.reportedDependencies = reportedDependencies;
  }

  public Map<String, MavenTargetInfo> mavenTargets;
  public Map<String, String> reportedDependencies;
}

/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe.json;

import java.util.Map;
import java.util.Set;

public class MavenExtractedInfo {
  public MavenExtractedInfo(Map<String, MavenTargetInfo> mavenTargets, Map<String, MavenDependencyInfo> reportedDependencies, Set<String> pomFilesProcessed) {
    this.mavenTargets = mavenTargets;
    this.reportedDependencies = reportedDependencies;
    this.pomFilesProcessed = pomFilesProcessed;
  }

  public Map<String, MavenTargetInfo> mavenTargets;
  public Map<String, MavenDependencyInfo> reportedDependencies;
  public Set<String> pomFilesProcessed;
}

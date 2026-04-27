/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe.json;

import java.util.Collection;
import java.util.Map;

public class MBTInfo {
  public MBTInfo(Map<String, MBTTargetInfo> targets, Collection<MBTDependencyModuleInfo> dependencyModules) {
    this.namespaces = targets;
    this.dependencyModules = dependencyModules;
  }

  public Map<String, MBTTargetInfo> namespaces;
  public Collection<MBTDependencyModuleInfo> dependencyModules;
}

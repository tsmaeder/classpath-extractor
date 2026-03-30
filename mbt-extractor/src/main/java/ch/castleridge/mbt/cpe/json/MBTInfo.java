/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe.json;

import java.util.Collection;

public class MBTInfo {
  public MBTInfo(Collection<MBTTargetInfo> targets, Collection<MBTDependencyModuleInfo> dependencyModules) {
    this.targets = targets;
    this.dependencyModules = dependencyModules;
  }

  public Collection<MBTTargetInfo> targets;
  public Collection<MBTDependencyModuleInfo> dependencyModules;
}

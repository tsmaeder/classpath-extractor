/*
 * Copyright (c) 2026 Anysphere, Inc.
 *
 * @author Thomas Mäder
 */
package ch.castleridge.mbt.cpe.json;

import java.util.List;

public class MBTTargetInfo {
  public List<String> compilerOptions;
  public String javaHome;
  public List<String> sources; // source folders
  public List<String> classes; // class folders
  public List<String> dependencyModules;
  public List<String> dependsOn;
}

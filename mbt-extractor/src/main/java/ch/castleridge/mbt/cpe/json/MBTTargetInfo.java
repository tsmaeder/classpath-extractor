package ch.castleridge.mbt.cpe.json;

import java.util.List;

public class MBTTargetInfo {
  public String id;
  public List<String> javacOptions;
  public String jdk;
  public List<String> sources; // source folders
  public List<String> classes; // class folders
  public List<String> dependencyModules;
}

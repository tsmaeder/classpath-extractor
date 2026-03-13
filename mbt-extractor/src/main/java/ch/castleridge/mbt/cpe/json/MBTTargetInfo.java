package ch.castleridge.mbt.cpe.json;

import java.util.List;
import java.util.Map;

public class MBTTargetInfo {
  public String id;
  public Map<String, String> javacOptions;
  public String jdk;
  public List<String> sources;
  public List<String> dependencyModules;
}

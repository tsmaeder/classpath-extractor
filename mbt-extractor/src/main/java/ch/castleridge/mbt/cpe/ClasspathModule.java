package ch.castleridge.mbt.cpe;

/**
 * Represents a module entry in the classpath JSON produced by the
 * classpath-extractor-maven-plugin. Only the {@code pom} field is required
 * for the MBT extractor algorithm.
 */
public class ClasspathModule {

  private String pom;

  public String getPom() {
    return pom;
  }

  public void setPom(String pom) {
    this.pom = pom;
  }
}

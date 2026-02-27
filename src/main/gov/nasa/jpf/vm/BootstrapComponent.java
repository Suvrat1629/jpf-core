package gov.nasa.jpf.vm;

/**
 * Simple descriptor for a bootstrap component accessor derived from cpArgs.
 */
public class BootstrapComponent {
  public final String owner;
  public final String name;
  public final String desc;
  public final int refKind;

  public BootstrapComponent(String owner, String name, String desc, int refKind) {
    this.owner = owner;
    this.name = name;
    this.desc = desc;
    this.refKind = refKind;
  }

  @Override
  public String toString() {
    return "BootstrapComponent[owner=" + owner + ", name=" + name + ", desc=" + desc + ", refKind=" + refKind + "]";
  }
}

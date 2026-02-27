package gov.nasa.jpf.vm;

/**
 * Simple blueprint describing expected bootstrap behavior produced by a host-side BSM resolver.
 * This is intentionally small and conservative: it contains component type signatures and
 * best-effort accessor descriptors captured from classfile cpArgs or a host-side BSM invocation.
 */
public class BootstrapBlueprint {
  public final String[] componentTypeNames;
  public final BootstrapComponent[] accessors;
  public final String bmType; // optional, e.g. "OBJECT_METHODS", "STRING_CONCATENATION"

  public BootstrapBlueprint(String[] componentTypeNames, BootstrapComponent[] accessors, String bmType){
    this.componentTypeNames = componentTypeNames == null ? null : componentTypeNames.clone();
    this.accessors = accessors == null ? null : accessors.clone();
    this.bmType = bmType;
  }
}

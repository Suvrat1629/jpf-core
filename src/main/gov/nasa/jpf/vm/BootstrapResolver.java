package gov.nasa.jpf.vm;

/**
 * Produce a conservative BootstrapBlueprint from available classfile-parsed data.
 * This is the central place to extend with a host-JVM BSM invocation in future.
 */
public class BootstrapResolver {

  public static BootstrapBlueprint resolve(BootstrapMethodInfo bmi) {
    if (bmi == null) return null;
    String[] compTypes = bmi.getComponentTypeNames();
    BootstrapComponent[] accessors = bmi.getBootstrapComponents();
    String bmType = bmi.getBmType() == null ? null : bmi.getBmType().name();
    return new BootstrapBlueprint(compTypes, accessors, bmType);
  }
}

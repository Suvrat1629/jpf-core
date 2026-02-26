package gov.nasa.jpf.vm;

/**
 * Simple descriptor for a record component accessor derived from bootstrap cpArgs.
 */
public class RecordComponent {
  // owner in dot notation, e.g. "com.example.MyRecord" (may be null)
  public final String owner;
  // member name (method or field)
  public final String name;
  // descriptor (JVM field or method descriptor). For methods this is the method descriptor.
  public final String desc;
  // reference kind if known (ClassFile.REF_*), 0 if unknown
  public final int refKind;

  public RecordComponent(String owner, String name, String desc, int refKind) {
    this.owner = owner;
    this.name = name;
    this.desc = desc;
    this.refKind = refKind;
  }

  @Override
  public String toString() {
    return "RecordComponent[owner=" + owner + ", name=" + name + ", desc=" + desc + ", refKind=" + refKind + "]";
  }
}

package gov.nasa.jpf.vm;

/**
 * Container for generated class artifacts produced by CallSiteGenerator.
 */
public final class GeneratedClassInfo {
    private final String className;
    private final byte[] classBytes;

    public GeneratedClassInfo(String className, byte[] classBytes) {
        this.className = className;
        this.classBytes = classBytes;
    }

    public String getClassName() {
        return className;
    }

    public byte[] getClassBytes() {
        return classBytes;
    }

    public boolean hasBytes() {
        return classBytes != null && classBytes.length > 0;
    }
}

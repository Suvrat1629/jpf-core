package gov.nasa.jpf.vm;

import java.util.Arrays;
import java.util.Objects;

/**
 * Descriptor for a bootstrap call-site to be used by CallSiteGenerator.
 */
public final class CallSiteDescriptor {
    private final String ownerClassName;
    private final String invokedName;
    private final String invokedDescriptor;
    private final byte[] bsmKey;
    // Optional component type names (for Record/ObjectMethods helpers) in JVM signature form, e.g. "Ljava/lang/String;"
    private final String[] componentTypeNames;

    public CallSiteDescriptor(String ownerClassName, String invokedName, String invokedDescriptor, byte[] bsmKey) {
        this.ownerClassName = ownerClassName;
        this.invokedName = invokedName;
        this.invokedDescriptor = invokedDescriptor;
        this.bsmKey = bsmKey == null ? null : Arrays.copyOf(bsmKey, bsmKey.length);
        this.componentTypeNames = null;
    }

    /**
     * Construct a descriptor with explicit record component types (JVM signatures).
     */
    public CallSiteDescriptor(String ownerClassName, String invokedName, String invokedDescriptor, byte[] bsmKey, String[] componentTypeNames) {
        this.ownerClassName = ownerClassName;
        this.invokedName = invokedName;
        this.invokedDescriptor = invokedDescriptor;
    this.bsmKey = bsmKey == null ? null : Arrays.copyOf(bsmKey, bsmKey.length);
        this.componentTypeNames = componentTypeNames == null ? null : Arrays.copyOf(componentTypeNames, componentTypeNames.length);
    }

    public String getOwnerClassName() {
        return ownerClassName;
    }

    public String getInvokedName() {
        return invokedName;
    }

    public String getInvokedDescriptor() {
        return invokedDescriptor;
    }

    public byte[] getBsmKey() {
        return bsmKey == null ? null : Arrays.copyOf(bsmKey, bsmKey.length);
    }

    public String[] getComponentTypeNames() {
        return componentTypeNames == null ? null : Arrays.copyOf(componentTypeNames, componentTypeNames.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallSiteDescriptor that = (CallSiteDescriptor) o;
    return Objects.equals(ownerClassName, that.ownerClassName) &&
        Objects.equals(invokedName, that.invokedName) &&
        Objects.equals(invokedDescriptor, that.invokedDescriptor) &&
        Arrays.equals(bsmKey, that.bsmKey) &&
        Arrays.equals(componentTypeNames, that.componentTypeNames);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(ownerClassName, invokedName, invokedDescriptor);
        result = 31 * result + Arrays.hashCode(bsmKey);
        result = 31 * result + Arrays.hashCode(componentTypeNames);
        return result;
    }
}

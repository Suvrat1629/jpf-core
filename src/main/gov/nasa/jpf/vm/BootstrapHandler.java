package gov.nasa.jpf.vm;

/**
 * Pluggable handler for different bootstrap method types.
 * This is the main extension point for new Java 11/17+ features.
 */
public interface BootstrapHandler {

    boolean canHandle(BootstrapMethodInfo bmi);

    /**
     * Generate the helper class (if needed) and compute/push the result to the stack.
     * Returns true if a repeatInvocation was requested (caller should return immediately).
     */
    boolean handle(ThreadInfo ti, BootstrapMethodInfo bmi, String invokedName,
                   String[] freeVariableTypeNames, Object[] freeVariableValues);
}

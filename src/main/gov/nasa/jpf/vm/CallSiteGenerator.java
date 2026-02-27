package gov.nasa.jpf.vm;

/**
 * Generates adapter classes for bootstrap call-sites.
 */
public interface CallSiteGenerator {
    /**
     * Generate an adapter for the given descriptor. Implementations may return
     * a GeneratedClassInfo with raw class bytes or a JPF ClassInfo-backed result.
     */
    GeneratedClassInfo generateAdapter(CallSiteDescriptor desc) throws GenerationException;
}


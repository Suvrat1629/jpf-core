package gov.nasa.jpf.vm;

/**
 * Exception thrown when generation of adapters fails.
 */
public class GenerationException extends Exception {
    public GenerationException(String message) {
        super(message);
    }

    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

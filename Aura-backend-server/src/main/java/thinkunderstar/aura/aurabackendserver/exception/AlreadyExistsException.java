package thinkunderstar.aura.aurabackendserver.exception;

/**
 * code:409
 */
public class AlreadyExistsException extends RuntimeException {
    public AlreadyExistsException(String message) {
        super(message);
    }
}

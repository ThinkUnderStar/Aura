package thinkunderstar.aura.aurabackendserver.exception;

/**
 * code:500
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}

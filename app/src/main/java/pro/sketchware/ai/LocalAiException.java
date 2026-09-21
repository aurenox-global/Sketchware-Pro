package pro.sketchware.ai;

public class LocalAiException extends Exception {
    public LocalAiException(String message) {
        super(message);
    }

    public LocalAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
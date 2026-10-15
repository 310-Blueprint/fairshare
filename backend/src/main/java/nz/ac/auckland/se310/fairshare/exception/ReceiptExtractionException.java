package nz.ac.auckland.se310.fairshare.exception;

public class ReceiptExtractionException extends RuntimeException {

    public ReceiptExtractionException(String message) {
        super(message);
    }

    public ReceiptExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}

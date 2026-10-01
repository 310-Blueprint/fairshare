package nz.ac.auckland.se310.fairshare.exception;

public class UnsupportedCurrencyException extends RuntimeException {

    public UnsupportedCurrencyException(String code) {
        super("Unsupported currency code: " + code);
    }
}

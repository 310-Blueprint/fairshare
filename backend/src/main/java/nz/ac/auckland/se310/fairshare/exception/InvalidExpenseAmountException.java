package nz.ac.auckland.se310.fairshare.exception;

public class InvalidExpenseAmountException extends RuntimeException {

    public InvalidExpenseAmountException(String message) {
        super(message);
    }
}

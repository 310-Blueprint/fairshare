package nz.ac.auckland.se310.fairshare.exception;

public class InvalidEndDateException extends RuntimeException {

    public InvalidEndDateException() {
        super("End date cannot be before the start date");
    }
}

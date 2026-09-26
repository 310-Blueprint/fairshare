package nz.ac.auckland.se310.fairshare.exception;

public class RecurringExpenseNotFoundException extends RuntimeException {

    public RecurringExpenseNotFoundException() {
        super("Recurring expense not found in group");
    }
}

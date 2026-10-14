package nz.ac.auckland.se310.fairshare.exception;

public class ExpenseAccessDeniedException extends RuntimeException {
    public ExpenseAccessDeniedException() {
        super("Only the creator of this expense can edit it");
    }
}

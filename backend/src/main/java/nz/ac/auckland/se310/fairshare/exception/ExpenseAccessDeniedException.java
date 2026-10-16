package nz.ac.auckland.se310.fairshare.exception;

public class ExpenseAccessDeniedException extends RuntimeException {
    public ExpenseAccessDeniedException() {
        this("Only the creator of this expense can edit it");
    }

    private ExpenseAccessDeniedException(String message) {
        super(message);
    }

    public static ExpenseAccessDeniedException forDelete() {
        return new ExpenseAccessDeniedException("Only the creator of this expense can delete it");
    }
}

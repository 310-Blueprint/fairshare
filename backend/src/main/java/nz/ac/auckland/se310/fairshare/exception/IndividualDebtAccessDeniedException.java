package nz.ac.auckland.se310.fairshare.exception;

// #2 AC7: only the creator of an individual debt entry may edit it, and only the person who is owed may delete it.
public class IndividualDebtAccessDeniedException extends RuntimeException {

    public IndividualDebtAccessDeniedException() {
        this("Only the creator of this entry can edit it");
    }

    private IndividualDebtAccessDeniedException(String message) {
        super(message);
    }

    public static IndividualDebtAccessDeniedException forDelete() {
        return new IndividualDebtAccessDeniedException("Only the person who is owed can delete this entry");
    }
}

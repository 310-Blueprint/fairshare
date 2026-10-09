package nz.ac.auckland.se310.fairshare.exception;

// #2 AC7: only the creator of an individual debt entry may edit or delete it.
public class IndividualDebtAccessDeniedException extends RuntimeException {

    public IndividualDebtAccessDeniedException() {
        super("Only the creator of this entry can edit or delete it");
    }
}

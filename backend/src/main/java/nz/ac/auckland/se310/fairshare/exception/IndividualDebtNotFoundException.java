package nz.ac.auckland.se310.fairshare.exception;

public class IndividualDebtNotFoundException extends RuntimeException {

    public IndividualDebtNotFoundException() {
        super("Individual debt entry not found");
    }
}

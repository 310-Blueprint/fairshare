package nz.ac.auckland.se310.fairshare.exception;

// #16 AC4: a member can only see their own spending overview.
public class SpendingAccessDeniedException extends RuntimeException {

    public SpendingAccessDeniedException() {
        super("You can only view your own spending");
    }
}

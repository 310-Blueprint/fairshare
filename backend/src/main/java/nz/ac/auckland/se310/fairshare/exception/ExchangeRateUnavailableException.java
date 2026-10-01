package nz.ac.auckland.se310.fairshare.exception;

/**
 * #14 AC2: no exchange rate could be found, because the rate service is unreachable, failed,
 * or does not quote the pair. The expense is rejected rather than saved unconverted, since an
 * unconverted amount cannot be added to balances kept in the group's currency.
 */
public class ExchangeRateUnavailableException extends RuntimeException {

    public ExchangeRateUnavailableException(String from, String to, Throwable cause) {
        super("The exchange rate from " + from + " to " + to + " is unavailable right now, so the "
                + "expense was not saved. Try again later, or enter the expense in " + to + ".", cause);
    }

    public ExchangeRateUnavailableException(String from, String to) {
        this(from, to, null);
    }
}

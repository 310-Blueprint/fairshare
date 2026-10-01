package nz.ac.auckland.se310.fairshare.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** #14: the HTTP responses the frontend relies on for currency errors. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void ac2_unavailableRateIsA503WithAnExplanation() {
        ResponseEntity<Map<String, String>> response = handler.handleExchangeRateUnavailable(
                new ExchangeRateUnavailableException("USD", "NZD"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("error",
                "The exchange rate from USD to NZD is unavailable right now, so the expense was "
                        + "not saved. Try again later, or enter the expense in NZD.");
    }

    @Test
    void ac3_unsupportedCurrencyIsA400OnTheCurrencyField() {
        ResponseEntity<Map<String, String>> response = handler.handleUnsupportedCurrency(
                new UnsupportedCurrencyException("XYZ"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("currency", "Unsupported currency code: XYZ");
    }
}

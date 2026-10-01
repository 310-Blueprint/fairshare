package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.controller.CurrencyController;
import nz.ac.auckland.se310.fairshare.dto.CurrencyResponse;
import nz.ac.auckland.se310.fairshare.exception.UnsupportedCurrencyException;
import nz.ac.auckland.se310.fairshare.model.User;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyServiceTest {

    private final CurrencyService currencyService = new CurrencyService();

    @Test
    void listsSupportedCurrenciesWithIsoCodesAndNames() {
        List<CurrencyResponse> currencies = currencyService.getSupportedCurrencies();

        assertThat(currencies).isNotEmpty();
        assertThat(currencies).allSatisfy(currency -> {
            assertThat(currency.code()).matches("^[A-Z]{3}$");
            assertThat(currency.name()).isNotBlank();
        });
        assertThat(currencies).contains(
                new CurrencyResponse("USD", "US Dollar"),
                new CurrencyResponse("NZD", "New Zealand Dollar"));
    }

    @Test
    void listIsSortedByCode() {
        List<String> codes = currencyService.getSupportedCurrencies().stream()
                .map(CurrencyResponse::code)
                .toList();

        assertThat(codes).isSorted();
    }

    @Test
    void everyGroupBaseCurrencyIsSupported() {
        // Expenses in the group's own currency must always be accepted.
        assertThat(Arrays.stream(User.Currency.values()).map(Enum::name))
                .allMatch(currencyService::isSupported);
    }

    @Test
    void acceptsCodesRegardlessOfCaseOrSurroundingSpaces() {
        assertThat(currencyService.isSupported("usd")).isTrue();
        assertThat(currencyService.isSupported(" USD ")).isTrue();
        assertThat(currencyService.requireSupported(" usd ")).isEqualTo("USD");
    }

    @Test
    void rejectsUnrecognisedOrMalformedCodes() {
        assertThat(currencyService.isSupported("XYZ")).isFalse();
        assertThat(currencyService.isSupported("US D")).isFalse();
        assertThat(currencyService.isSupported("")).isFalse();
        assertThat(currencyService.isSupported("   ")).isFalse();
        assertThat(currencyService.isSupported(null)).isFalse();
    }

    @Test
    void requireSupportedThrowsForUnsupportedCode() {
        assertThatCode(() -> currencyService.requireSupported("USD")).doesNotThrowAnyException();
        assertThatThrownBy(() -> currencyService.requireSupported(null))
                .isInstanceOf(UnsupportedCurrencyException.class);
        assertThatThrownBy(() -> currencyService.requireSupported("XYZ"))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessage("Unsupported currency code: XYZ");
    }

    @Test
    void controllerReturnsSupportedCurrencies() {
        CurrencyController controller = new CurrencyController(currencyService);

        assertThat(controller.list()).isEqualTo(currencyService.getSupportedCurrencies());
    }
}

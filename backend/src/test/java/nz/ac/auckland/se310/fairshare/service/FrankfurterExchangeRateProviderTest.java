package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.exception.ExchangeRateUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/** Checks the provider against canned Frankfurter responses; no real network calls are made. */
class FrankfurterExchangeRateProviderTest {

    private static final String BASE_URL = "https://rates.test/v1";
    private static final LocalDate DATE = LocalDate.of(2026, Month.AUGUST, 1);

    private MockRestServiceServer server;
    private FrankfurterExchangeRateProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new FrankfurterExchangeRateProvider(builder.build());
    }

    @Test
    void returnsTheRateForTheRequestedDateAndPair() {
        server.expect(requestTo(BASE_URL + "/2026-08-01?from=USD&to=NZD"))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-07-31\",\"rates\":{\"NZD\":1.7056}}",
                        MediaType.APPLICATION_JSON));

        BigDecimal rate = provider.getRate("USD", "NZD", DATE);

        assertThat(rate).isEqualByComparingTo("1.7056");
        server.verify();
    }

    @Test
    void ac2_serverErrorIsReportedAsUnavailable() {
        server.expect(requestTo(BASE_URL + "/2026-08-01?from=USD&to=NZD"))
                .andRespond(withServerError());

        assertUnavailable();
    }

    @Test
    void ac2_unreachableServiceIsReportedAsUnavailable() {
        server.expect(requestTo(BASE_URL + "/2026-08-01?from=USD&to=NZD"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertUnavailable();
    }

    @Test
    void ac2_notFoundIsReportedAsUnavailable() {
        // What Frankfurter returns for a currency it does not quote.
        server.expect(requestTo(BASE_URL + "/2026-08-01?from=USD&to=NZD"))
                .andRespond(withResourceNotFound()
                        .body("{\"message\":\"not found\"}").contentType(MediaType.APPLICATION_JSON));

        assertUnavailable();
    }

    @Test
    void ac2_responseWithoutARateForThePairIsReportedAsUnavailable() {
        server.expect(requestTo(BASE_URL + "/2026-08-01?from=USD&to=NZD"))
                .andRespond(withSuccess(
                        "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-07-31\",\"rates\":{}}",
                        MediaType.APPLICATION_JSON));

        assertUnavailable();
    }

    @Test
    void ac2_unreadableResponseIsReportedAsUnavailable() {
        server.expect(requestTo(BASE_URL + "/2026-08-01?from=USD&to=NZD"))
                .andRespond(withSuccess("<html>Service temporarily down</html>", MediaType.TEXT_HTML));

        assertUnavailable();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> provider.getRate("USD", "NZD", DATE))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("from USD to NZD is unavailable")
                .hasMessageContaining("was not saved");
        server.verify();
    }

    @Test
    void sameCurrencyIsOneWithoutCallingTheService() {
        assertThat(provider.getRate("NZD", "NZD", DATE)).isEqualByComparingTo("1");
        server.verify(); // fails if any request was made
    }
}

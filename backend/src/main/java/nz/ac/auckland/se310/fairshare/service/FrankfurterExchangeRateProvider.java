package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.exception.ExchangeRateUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

/**
 * Rates from the Frankfurter API (European Central Bank reference rates). It is free and needs
 * no API key. A date with no published rate, such as a weekend, returns the latest earlier one.
 */
@Component
public class FrankfurterExchangeRateProvider implements ExchangeRateProvider {

    // Saving an expense waits on this call, so fail fast rather than leave the form hanging.
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    @Autowired
    public FrankfurterExchangeRateProvider(@Value("${fairshare.exchange-rate.base-url}") String baseUrl) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeoutRequestFactory())
                .build());
    }

    FrankfurterExchangeRateProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public BigDecimal getRate(String from, String to, LocalDate date) {
        if (from.equals(to)) {
            return BigDecimal.ONE;
        }

        FrankfurterResponse response;
        try {
            response = restClient.get()
                    .uri("/{date}?from={from}&to={to}", date, from, to)
                    .retrieve()
                    .body(FrankfurterResponse.class);
        } catch (RestClientException e) {
            // Covers timeouts, connection failures, error statuses and unreadable responses.
            throw new ExchangeRateUnavailableException(from, to, e); // AC2
        }

        BigDecimal rate = response == null || response.rates() == null ? null : response.rates().get(to);
        if (rate == null || rate.signum() <= 0) {
            throw new ExchangeRateUnavailableException(from, to); // AC2: no rate for this pair
        }
        return rate;
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    // Only the field we need, e.g. {"base":"USD","date":"2026-07-31","rates":{"NZD":1.7056}}
    record FrankfurterResponse(Map<String, BigDecimal> rates) {}
}

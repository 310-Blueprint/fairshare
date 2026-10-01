package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.exception.ExchangeRateUnavailableException;
import nz.ac.auckland.se310.fairshare.service.ExchangeRateProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Replaces the real exchange rate service with fixed rates so tests never touch the network. */
@TestConfiguration
public class TestExchangeRateConfig {

    public static class StubExchangeRateProvider implements ExchangeRateProvider {
        private final Map<String, BigDecimal> rates = new HashMap<>();

        public void setRate(String from, String to, String rate) {
            rates.put(from + "->" + to, new BigDecimal(rate));
        }

        public void reset() {
            rates.clear();
        }

        @Override
        public BigDecimal getRate(String from, String to, LocalDate date) {
            if (from.equals(to)) {
                return BigDecimal.ONE;
            }
            BigDecimal rate = rates.get(from + "->" + to);
            if (rate == null) {
                // Behaves like the real provider when the service is down or lacks the pair.
                throw new ExchangeRateUnavailableException(from, to);
            }
            return rate;
        }
    }

    @Bean
    @Primary
    public StubExchangeRateProvider exchangeRateProvider() {
        return new StubExchangeRateProvider();
    }
}

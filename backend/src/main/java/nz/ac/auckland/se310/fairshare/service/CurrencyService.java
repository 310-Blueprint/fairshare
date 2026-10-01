package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.CurrencyResponse;
import nz.ac.auckland.se310.fairshare.exception.UnsupportedCurrencyException;
import nz.ac.auckland.se310.fairshare.model.User;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class CurrencyService {

    // User.Currency is the one list of supported codes, so profiles, groups and expenses agree.
    private static final Set<String> SUPPORTED_CODES = Arrays.stream(User.Currency.values())
            .map(Enum::name)
            .collect(Collectors.toCollection(TreeSet::new));

    private static final List<CurrencyResponse> SUPPORTED_CURRENCIES = SUPPORTED_CODES.stream()
            .map(code -> new CurrencyResponse(
                    code, Currency.getInstance(code).getDisplayName(Locale.ENGLISH)))
            .toList();

    // AC3: supported currencies with their ISO 4217 codes, sorted by code.
    public List<CurrencyResponse> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    // Case and surrounding spaces are ignored, so " usd " is read as USD.
    public boolean isSupported(String code) {
        return code != null && SUPPORTED_CODES.contains(normalise(code));
    }

    /** Returns the canonical code (e.g. "USD" for " usd "), or throws if it is not supported. */
    public String requireSupported(String code) {
        if (!isSupported(code)) {
            throw new UnsupportedCurrencyException(code); // AC3
        }
        return normalise(code);
    }

    private static String normalise(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}

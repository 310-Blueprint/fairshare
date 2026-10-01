package nz.ac.auckland.se310.fairshare.controller;

import nz.ac.auckland.se310.fairshare.dto.CurrencyResponse;
import nz.ac.auckland.se310.fairshare.service.CurrencyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/currencies")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping
    public List<CurrencyResponse> list() {
        return currencyService.getSupportedCurrencies();
    }
}

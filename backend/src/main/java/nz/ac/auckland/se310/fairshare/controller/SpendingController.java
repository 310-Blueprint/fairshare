package nz.ac.auckland.se310.fairshare.controller;

import nz.ac.auckland.se310.fairshare.dto.SpendingSummaryResponse;
import nz.ac.auckland.se310.fairshare.security.CurrentUserProvider;
import nz.ac.auckland.se310.fairshare.service.SpendingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/users/{userId}/spending")
public class SpendingController {

    private final SpendingService spendingService;
    private final CurrentUserProvider currentUser;

    public SpendingController(SpendingService spendingService, CurrentUserProvider currentUser) {
        this.spendingService = spendingService;
        this.currentUser = currentUser;
    }

    // #16: month is "yyyy-MM"; left out, the current month is summarised (AC1).
    @GetMapping
    public SpendingSummaryResponse summary(@PathVariable Long userId,
                                           @RequestParam(required = false) YearMonth month) {
        return spendingService.getSummary(userId, currentUser.currentUserId(), month);
    }
}

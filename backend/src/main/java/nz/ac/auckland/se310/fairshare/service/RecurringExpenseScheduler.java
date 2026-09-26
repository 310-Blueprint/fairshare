package nz.ac.auckland.se310.fairshare.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringExpenseScheduler {

    private final RecurringExpenseGenerationService generationService;

    public RecurringExpenseScheduler(RecurringExpenseGenerationService generationService) {
        this.generationService = generationService;
    }

    // AC2: runs once a day so due recurring expenses are generated without manual re-entry.
    @Scheduled(cron = "0 0 0 * * *")
    public void generateDueExpenses() {
        generationService.generateDueExpenses();
    }
}

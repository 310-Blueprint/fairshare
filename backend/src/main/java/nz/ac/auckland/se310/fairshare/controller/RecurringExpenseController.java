package nz.ac.auckland.se310.fairshare.controller;

import jakarta.validation.Valid;
import nz.ac.auckland.se310.fairshare.dto.CreateRecurringExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.RecurringExpenseResponse;
import nz.ac.auckland.se310.fairshare.security.CurrentUserProvider;
import nz.ac.auckland.se310.fairshare.service.RecurringExpenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/groups/{groupId}/recurring-expenses")
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;
    private final CurrentUserProvider currentUser;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService, CurrentUserProvider currentUser) {
        this.recurringExpenseService = recurringExpenseService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<RecurringExpenseResponse> create(@PathVariable Long groupId,
                                                             @Valid @RequestBody CreateRecurringExpenseRequest request) {
        RecurringExpenseResponse created = recurringExpenseService.createRecurringExpense(groupId, request, currentUser.currentUserId());
        return ResponseEntity
                .created(URI.create("/groups/" + groupId + "/recurring-expenses/" + created.id()))
                .body(created);
    }

    @GetMapping
    public List<RecurringExpenseResponse> list(@PathVariable Long groupId) {
        return recurringExpenseService.getRecurringExpensesForGroup(groupId, currentUser.currentUserId());
    }

    @GetMapping("/{recurringExpenseId}")
    public ResponseEntity<RecurringExpenseResponse> get(
            @PathVariable Long groupId,
            @PathVariable Long recurringExpenseId) {
        return ResponseEntity.ok(
                recurringExpenseService.getRecurringExpense(groupId, recurringExpenseId, currentUser.currentUserId()));
    }
}

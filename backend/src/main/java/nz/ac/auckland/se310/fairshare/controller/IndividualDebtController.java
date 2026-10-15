package nz.ac.auckland.se310.fairshare.controller;

import jakarta.validation.Valid;
import nz.ac.auckland.se310.fairshare.dto.CounterpartyBalanceResponse;
import nz.ac.auckland.se310.fairshare.dto.CreateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.dto.IndividualDebtResponse;
import nz.ac.auckland.se310.fairshare.dto.NetBalanceResponse;
import nz.ac.auckland.se310.fairshare.dto.UpdateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.security.CurrentUserProvider;
import nz.ac.auckland.se310.fairshare.service.IndividualDebtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/individual-debts")
public class IndividualDebtController {

    private final IndividualDebtService individualDebtService;
    private final CurrentUserProvider currentUser;

    public IndividualDebtController(IndividualDebtService individualDebtService, CurrentUserProvider currentUser) {
        this.individualDebtService = individualDebtService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<IndividualDebtResponse> create(@Valid @RequestBody CreateIndividualDebtRequest request) {
        IndividualDebtResponse created = individualDebtService.createDebt(currentUser.currentUserId(), request);
        return ResponseEntity
                .created(URI.create("/individual-debts/" + created.id()))
                .body(created);
    }

    @GetMapping
    public List<IndividualDebtResponse> list() {
        return individualDebtService.listMyDebts(currentUser.currentUserId());
    }

    @PutMapping("/{id}")
    public IndividualDebtResponse update(@PathVariable Long id, @Valid @RequestBody UpdateIndividualDebtRequest request) {
        return individualDebtService.updateDebt(id, currentUser.currentUserId(), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        individualDebtService.deleteDebt(id, currentUser.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/balances")
    public List<CounterpartyBalanceResponse> balances() {
        return individualDebtService.getBalancesOverview(currentUser.currentUserId());
    }

    @GetMapping("/balances/{otherUserId}")
    public NetBalanceResponse balanceWith(@PathVariable Long otherUserId) {
        return individualDebtService.getNetBalance(currentUser.currentUserId(), otherUserId);
    }
}

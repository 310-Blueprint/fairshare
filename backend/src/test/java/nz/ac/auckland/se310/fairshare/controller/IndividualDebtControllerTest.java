package nz.ac.auckland.se310.fairshare.controller;

import nz.ac.auckland.se310.fairshare.dto.CreateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.dto.IndividualDebtResponse;
import nz.ac.auckland.se310.fairshare.dto.NetBalanceResponse;
import nz.ac.auckland.se310.fairshare.dto.UpdateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.exception.GlobalExceptionHandler;
import nz.ac.auckland.se310.fairshare.exception.IndividualDebtAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.InvalidDebtEntryException;
import nz.ac.auckland.se310.fairshare.service.IndividualDebtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #2: the HTTP contract of /individual-debts - status codes and error bodies the frontend relies on.
 * The service is stubbed; authentication is replaced by a fixed current user.
 */
class IndividualDebtControllerTest {

    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final String VALID_UPDATE = """
            {"amount": 15.00, "description": "Dinner", "date": "2026-08-01", "currency": "NZD"}
            """;

    private final IndividualDebtService service = mock(IndividualDebtService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new IndividualDebtController(service, () -> ALICE))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReturns201WithTheLocationOfTheNewEntry() throws Exception {
        when(service.createDebt(eq(ALICE), any(CreateIndividualDebtRequest.class))).thenReturn(debt(5L));

        mockMvc.perform(post("/individual-debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"counterpartyIdentifier": "bob@example.com", "amount": 25.00,
                                 "description": "Lunch", "date": "2026-08-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/individual-debts/5"))
                .andExpect(jsonPath("$.currency").value("NZD"));
    }

    @Test
    void ac9_invalidFieldsAreA400KeyedByField() throws Exception {
        mockMvc.perform(post("/individual-debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"counterpartyIdentifier": "", "amount": -1, "description": "", "date": "2026-08-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.counterpartyIdentifier").value("Counterparty is required"))
                .andExpect(jsonPath("$.description").value("Description is required"))
                .andExpect(jsonPath("$.amount").exists());
        verify(service, never()).createDebt(any(), any());
    }

    @Test
    void ac9_selfDebtIsA400WithAFormLevelError() throws Exception {
        when(service.createDebt(eq(ALICE), any(CreateIndividualDebtRequest.class)))
                .thenThrow(new InvalidDebtEntryException("The counterparty cannot be yourself"));

        mockMvc.perform(post("/individual-debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"counterpartyIdentifier": "alice@example.com", "amount": 25.00,
                                 "description": "Lunch", "date": "2026-08-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The counterparty cannot be yourself"));
    }

    @Test
    void updateValidatesTheEditableFields() throws Exception {
        mockMvc.perform(put("/individual-debts/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 0, "description": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount").exists())
                .andExpect(jsonPath("$.description").value("Description is required"))
                .andExpect(jsonPath("$.date").value("Date is required"));
    }

    @Test
    void updateReturnsTheSavedEntry() throws Exception {
        when(service.updateDebt(eq(5L), eq(ALICE), any(UpdateIndividualDebtRequest.class))).thenReturn(debt(5L));

        mockMvc.perform(put("/individual-debts/5").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void ac7_editingSomeoneElsesEntryIsA403() throws Exception {
        when(service.updateDebt(eq(5L), eq(ALICE), any(UpdateIndividualDebtRequest.class)))
                .thenThrow(new IndividualDebtAccessDeniedException());

        mockMvc.perform(put("/individual-debts/5").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void ac7_deletingAnEntryYouAreNotOwedOnIsA403() throws Exception {
        doThrow(IndividualDebtAccessDeniedException.forDelete()).when(service).deleteDebt(5L, ALICE);

        mockMvc.perform(delete("/individual-debts/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the person who is owed can delete this entry"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/individual-debts/5"))
                .andExpect(status().isNoContent());
        verify(service).deleteDebt(5L, ALICE);
    }

    @Test
    void balanceWithAUserIncludesTheDisplayCurrency() throws Exception {
        when(service.getNetBalance(ALICE, BOB)).thenReturn(new NetBalanceResponse(
                BOB, "bob", BOB, ALICE, new BigDecimal("6.00"), "NZD", false, List.of()));

        mockMvc.perform(get("/individual-debts/balances/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(6.00))
                .andExpect(jsonPath("$.currency").value("NZD"))
                .andExpect(jsonPath("$.fromUserId").value(BOB));
    }

    private static IndividualDebtResponse debt(long id) {
        return new IndividualDebtResponse(id, ALICE, "alice", BOB, "bob", new BigDecimal("25.00"), "NZD",
                "Lunch", LocalDate.of(2026, 8, 1), true);
    }
}

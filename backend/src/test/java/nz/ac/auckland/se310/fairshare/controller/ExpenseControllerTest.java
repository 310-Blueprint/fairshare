package nz.ac.auckland.se310.fairshare.controller;

import nz.ac.auckland.se310.fairshare.exception.ExpenseAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.GlobalExceptionHandler;
import nz.ac.auckland.se310.fairshare.service.ExpenseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExpenseControllerTest {
    @Test
    void nonCreatorReceives403AndAnAuthorisationMessage() throws Exception {
        ExpenseService service = mock(ExpenseService.class);
        doThrow(new ExpenseAccessDeniedException()).when(service).updateExpense(eq(1L), any(), eq(2L), eq(9L));
        var mvc = MockMvcBuilders.standaloneSetup(new ExpenseController(service, () -> 2L))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(put("/groups/1/expenses/9").contentType(MediaType.APPLICATION_JSON).content("""
                {"amount": 30.00, "description": "Dinner", "paidByUserId": 2,
                 "participantUserIds": [1, 2], "expenseDate": "2026-10-14"}
                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the creator of this expense can edit it"));
    }
    @Test
    void nonCreatorDeletionReturns403() throws Exception {
        ExpenseService service = mock(ExpenseService.class);
        doThrow(ExpenseAccessDeniedException.forDelete()).when(service).deleteExpense(1L, 9L, 2L);
        var mvc = MockMvcBuilders.standaloneSetup(new ExpenseController(service, () -> 2L))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(delete("/groups/1/expenses/9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only the creator of this expense can delete it"));
    }

    @Test
    void creatorDeletionReturns204WithoutABody() throws Exception {
        ExpenseService service = mock(ExpenseService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ExpenseController(service, () -> 1L))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(delete("/groups/1/expenses/9"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).deleteExpense(1L, 9L, 1L);
    }
}

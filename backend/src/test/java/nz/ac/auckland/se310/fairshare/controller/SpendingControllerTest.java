package nz.ac.auckland.se310.fairshare.controller;

import nz.ac.auckland.se310.fairshare.dto.SpendingSummaryResponse;
import nz.ac.auckland.se310.fairshare.exception.GlobalExceptionHandler;
import nz.ac.auckland.se310.fairshare.exception.SpendingAccessDeniedException;
import nz.ac.auckland.se310.fairshare.service.SpendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #16: the HTTP contract of /users/{userId}/spending. The service is stubbed. */
class SpendingControllerTest {

    private static final long ALICE = 10L;
    private static final long BOB = 20L;

    private final SpendingService service = mock(SpendingService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SpendingController(service, () -> ALICE))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void ac1_withoutAMonthTheServiceSummarisesTheCurrentOne() throws Exception {
        when(service.getSummary(eq(ALICE), eq(ALICE), isNull())).thenReturn(summary("2026-10"));

        mockMvc.perform(get("/users/" + ALICE + "/spending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.total").value(12.5));
    }

    @Test
    void ac2_aMonthCanBeChosen() throws Exception {
        when(service.getSummary(ALICE, ALICE, YearMonth.of(2026, 8))).thenReturn(summary("2026-08"));

        mockMvc.perform(get("/users/" + ALICE + "/spending").param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-08"));
    }

    @Test
    void aMalformedMonthIsA400() throws Exception {
        mockMvc.perform(get("/users/" + ALICE + "/spending").param("month", "August"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ac4_anotherUsersOverviewIsA403() throws Exception {
        when(service.getSummary(eq(BOB), eq(ALICE), any())).thenThrow(new SpendingAccessDeniedException());

        mockMvc.perform(get("/users/" + BOB + "/spending"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You can only view your own spending"));
    }

    private static SpendingSummaryResponse summary(String month) {
        return new SpendingSummaryResponse(month, "NZD", new BigDecimal("12.50"),
                List.of(), List.of(), List.of(), List.of());
    }
}

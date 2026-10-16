package nz.ac.auckland.se310.fairshare.dto;

import java.time.Instant;
import nz.ac.auckland.se310.fairshare.model.ExpenseAudit;

public record ExpenseActivityResponse(Long id, Long expenseId, String expenseDescription,
                                      Long actorUserId, String actorUsername,
                                      ExpenseAudit.Action action, Instant changedAt) {}

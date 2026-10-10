package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.ExpenseShare;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * #12: builds downloadable exports of a group's expense data. Everything is read from the
 * database on each call, so an export always reflects the group as it is right now (AC3).
 */
@Service
public class GroupExportService {

    // Excel only reads a CSV as UTF-8 when it starts with a byte order mark (AC5).
    private static final String UTF8_BOM = "﻿";
    private static final String CSV_LINE_END = "\r\n";
    private static final List<String> CSV_HEADER = List.of(
            "Date", "Description", "Amount", "Currency",
            "Original Amount", "Original Currency", "Paid By", "Split");

    private final ExpenseGroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;

    public GroupExportService(ExpenseGroupRepository groupRepository, ExpenseRepository expenseRepository,
                              ExpenseShareRepository expenseShareRepository) {
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
    }

    /**
     * AC1, AC5: one row per expense, oldest first, under a labelled header. A group with no
     * expenses still gets the header row (AC4).
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv(Long groupId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId); // AC6
        String currency = group.getBaseCurrency().name();
        Map<Long, List<ExpenseShare>> sharesByExpense = sharesByExpense(groupId);

        StringBuilder csv = new StringBuilder(UTF8_BOM);
        appendCsvRow(csv, CSV_HEADER);
        for (Expense expense : expenseRepository.findByGroupIdOrderByExpenseDateAscIdAsc(groupId)) {
            appendCsvRow(csv, List.of(
                    expense.getExpenseDate().toString(),
                    expense.getDescription(),
                    expense.getAmount().toPlainString(),
                    currency,
                    expense.getOriginalAmount().toPlainString(),
                    expense.getOriginalCurrency(),
                    expense.getPaidBy().getUsername(),
                    splitSummary(sharesByExpense.getOrDefault(expense.getId(), List.of()))));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private ExpenseGroup requireMemberGroup(Long groupId, Long currentUserId) {
        return groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);
    }

    private Map<Long, List<ExpenseShare>> sharesByExpense(Long groupId) {
        return expenseShareRepository.findByExpenseGroupId(groupId).stream()
                .collect(Collectors.groupingBy(share -> share.getExpense().getId()));
    }

    // e.g. "alice: 10.00; bob: 10.00", in user id order to match how leftover cents are allocated.
    private String splitSummary(List<ExpenseShare> shares) {
        return shares.stream()
                .sorted(Comparator.comparing(share -> share.getUser().getId()))
                .map(share -> share.getUser().getUsername() + ": " + share.getShareAmount().toPlainString())
                .collect(Collectors.joining("; "));
    }

    private void appendCsvRow(StringBuilder csv, List<String> cells) {
        csv.append(cells.stream().map(GroupExportService::csvCell).collect(Collectors.joining(",")));
        csv.append(CSV_LINE_END);
    }

    /**
     * AC5: quotes a cell holding a comma, quote or line break, doubling any quotes inside it.
     * A cell that Excel would read as a formula is prefixed with an apostrophe so it shows as text.
     */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String cell = startsLikeFormula(value) ? "'" + value : value;
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }

    private static boolean startsLikeFormula(String value) {
        return !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0;
    }
}

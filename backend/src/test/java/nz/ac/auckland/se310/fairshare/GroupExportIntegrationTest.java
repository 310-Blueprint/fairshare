package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.dto.CreateExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.dto.MemberBalance;
import nz.ac.auckland.se310.fairshare.dto.SettlementRequest;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.repository.SettlementRepository;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
import nz.ac.auckland.se310.fairshare.service.ExpenseService;
import nz.ac.auckland.se310.fairshare.service.GroupExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #12: exporting a group's expense data. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestCurrentUserConfig.class, TestExchangeRateConfig.class})
class GroupExportIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final String CAROL_EMAIL = "carol@test.com";
    private static final String CSV_HEADER =
            "Date,Description,Amount,Currency,Original Amount,Original Currency,Paid By,Split\r\n";
    private static final LocalDate SEPT_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEPT_5 = LocalDate.of(2026, 9, 5);

    @Autowired GroupExportService exportService;
    @Autowired ExpenseGroupService groupService;
    @Autowired ExpenseService expenseService;
    @Autowired ExpenseGroupRepository groupRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired ExpenseShareRepository expenseShareRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TestExchangeRateConfig.StubExchangeRateProvider exchangeRates;

    private Long aliceId;
    private Long bobId;
    private Long carolId;
    private Long groupId;

    @BeforeEach
    void setUp() {
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        // Recurring expenses reference the group by foreign key, so they must go before it.
        jdbcTemplate.update("DELETE FROM recurring_expense_participant");
        jdbcTemplate.update("DELETE FROM recurring_expense");
        settlementRepository.deleteAll();
        groupRepository.deleteAll();

        aliceId = userRepository.findByEmail("alice@test.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("bob@test.com").orElseThrow().getId();
        carolId = userRepository.findByEmail(CAROL_EMAIL)
                .orElseGet(() -> userRepository.save(new User(
                        "carol", "x", CAROL_EMAIL, User.Country.NEW_ZEALAND, User.Currency.NZD)))
                .getId();

        groupId = groupService.createGroup(new CreateGroupRequest("Flat 3", null), aliceId).id();
        groupService.addMember(groupId, "bob@test.com", aliceId);

        exchangeRates.reset();
        exchangeRates.setRate("USD", "NZD", "1.7056");
    }

    @Test
    void csvAc1_listsEveryExpenseOldestFirstWithPayerAndSplit() {
        groupService.addMember(groupId, CAROL_EMAIL, aliceId);
        // Recorded newest first, to show the export orders by expense date rather than insertion.
        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("20.00"), "Dinner", bobId, List.of(aliceId, bobId), SEPT_5, "USD"), aliceId);
        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("30.00"), "Groceries", aliceId, List.of(aliceId, bobId, carolId), SEPT_1), aliceId);

        String csv = csvText(exportService.exportCsv(groupId, aliceId));

        assertThat(csv).isEqualTo("﻿" + CSV_HEADER
                + "2026-09-01,Groceries,30.00,NZD,30.00,NZD,alice,alice: 10.00; bob: 10.00; carol: 10.00\r\n"
                // USD 20.00 at 1.7056 is NZD 34.11; the odd cent goes to the lowest user id.
                + "2026-09-05,Dinner,34.11,NZD,20.00,USD,bob,alice: 17.06; bob: 17.05\r\n");
    }

    @Test
    void csvAc3_reflectsAnExpenseEditedAfterAnEarlierExport() {
        ExpenseResponse expense = expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("10.00"), "Taxi", aliceId, List.of(aliceId, bobId), SEPT_1), aliceId);
        assertThat(csvText(exportService.exportCsv(groupId, aliceId))).contains("Taxi,10.00");

        expenseService.updateExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("24.00"), "Airport taxi", bobId, List.of(aliceId, bobId), SEPT_1), aliceId, expense.id());

        assertThat(csvText(exportService.exportCsv(groupId, aliceId)))
                .doesNotContain("Taxi,10.00")
                .contains("2026-09-01,Airport taxi,24.00,NZD,24.00,NZD,bob,alice: 12.00; bob: 12.00\r\n");
    }

    @Test
    void csvAc4_groupWithNoExpensesIsJustTheHeader() {
        assertThat(csvText(exportService.exportCsv(groupId, aliceId))).isEqualTo("﻿" + CSV_HEADER);
    }

    @Test
    void csvAc5_startsWithAUtf8ByteOrderMark() {
        byte[] csv = exportService.exportCsv(groupId, aliceId);

        assertThat(Arrays.copyOf(csv, 3)).containsExactly(0xEF, 0xBB, 0xBF);
    }

    @Test
    void csvAc5_escapesCommasQuotesNewlinesAndKeepsSpecialCharacters() {
        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("48.00"), "Dinner, \"Chez Zoë\"\n€24 set menu", aliceId, List.of(aliceId, bobId), SEPT_1),
                aliceId);
        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("6.00"), "=1+2", aliceId, List.of(aliceId, bobId), SEPT_5), aliceId);

        String csv = csvText(exportService.exportCsv(groupId, aliceId));

        assertThat(csv).isEqualTo("﻿" + CSV_HEADER
                + "2026-09-01,\"Dinner, \"\"Chez Zoë\"\"\n€24 set menu\",48.00,NZD,48.00,NZD,alice,alice: 24.00; bob: 24.00\r\n"
                + "2026-09-05,'=1+2,6.00,NZD,6.00,NZD,alice,alice: 3.00; bob: 3.00\r\n");
    }

    @Test
    void csvAc6_nonMemberIsRejected() {
        assertThatThrownBy(() -> exportService.exportCsv(groupId, carolId))
                .isInstanceOf(GroupAccessDeniedException.class);
    }

    @Test
    void csvAc6_unknownGroupIsRejectedTheSameWay() {
        assertThatThrownBy(() -> exportService.exportCsv(groupId + 1000, aliceId))
                .isInstanceOf(GroupAccessDeniedException.class);
    }

    @Test
    void pdfAc2_listsExpensesAndEachMembersPaidShareAndNetBalance() throws IOException {
        groupService.addMember(groupId, CAROL_EMAIL, aliceId);
        recordGroceriesAndDinner();

        byte[] pdf = exportService.exportPdf(groupId, aliceId);
        String text = pdfText(pdf);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(text).contains("Flat 3", "All amounts in NZD", "Expenses", "Member balances");
        assertThat(text).containsPattern(row("2026-09-01", "Groceries", "30.00", "alice"));
        assertThat(text).containsPattern(row("2026-09-05", "Dinner", "34.11 \\(USD 20.00\\)", "bob"));
        assertThat(text).containsPattern(row("alice: 10.00; bob: 10.00; carol: 10.00"));
        assertThat(text).containsPattern(row("alice: 17.06; bob: 17.05"));
        // Member, total paid, total share, net balance (positive owes, negative is owed).
        assertThat(text).containsPattern(row("alice", "30.00", "27.06", "-2.94"));
        assertThat(text).containsPattern(row("bob", "34.11", "27.05", "-7.06"));
        assertThat(text).containsPattern(row("carol", "0.00", "10.00", "10.00"));
    }

    @Test
    void pdfAc2_netBalanceMatchesTheBalancesEndpointIncludingPaidSettlements() throws IOException {
        groupService.addMember(groupId, CAROL_EMAIL, aliceId);
        recordGroceriesAndDinner();
        groupService.computeSettlement(groupId, carolId, new SettlementRequest(List.of()));
        groupService.markSettlementPaid(groupId, carolId, bobId, carolId);

        String text = pdfText(exportService.exportPdf(groupId, aliceId));

        // Carol's 7.06 to Bob clears his balance, though his paid and share totals are unchanged.
        assertThat(groupService.getBalances(groupId, aliceId))
                .extracting(MemberBalance::balance)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("-2.94"), BigDecimal.ZERO, new BigDecimal("2.94"));
        assertThat(text).containsPattern(row("alice", "30.00", "27.06", "-2.94"));
        assertThat(text).containsPattern(row("bob", "34.11", "27.05", "0.00"));
        assertThat(text).containsPattern(row("carol", "0.00", "10.00", "2.94"));
    }

    @Test
    void pdfAc3_reflectsAnExpenseAddedAfterAnEarlierExport() throws IOException {
        assertThat(pdfText(exportService.exportPdf(groupId, aliceId))).contains("No expenses recorded.");

        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("10.00"), "Taxi", aliceId, List.of(aliceId, bobId), SEPT_1), aliceId);

        assertThat(pdfText(exportService.exportPdf(groupId, aliceId)))
                .doesNotContain("No expenses recorded.")
                .containsPattern(row("2026-09-01", "Taxi", "10.00", "alice"));
    }

    @Test
    void pdfAc4_groupWithNoExpensesListsEveryMemberAtZero() throws IOException {
        String text = pdfText(exportService.exportPdf(groupId, aliceId));

        assertThat(text).contains("No expenses recorded.");
        assertThat(text).containsPattern(row("alice", "0.00", "0.00", "0.00"));
        assertThat(text).containsPattern(row("bob", "0.00", "0.00", "0.00"));
    }

    @Test
    void pdf_rendersNonLatinCharactersInNamesAndDescriptions() throws IOException {
        Long cafeGroupId = groupService.createGroup(new CreateGroupRequest("Café Zoë €", null), aliceId).id();
        expenseService.createExpense(cafeGroupId, new CreateExpenseRequest(
                new BigDecimal("12.00"), "Ужин – ΣΟΥΒΛΑΚΙ", aliceId, List.of(aliceId), SEPT_1), aliceId);

        assertThat(pdfText(exportService.exportPdf(cafeGroupId, aliceId)))
                .contains("Café Zoë €", "Ужин – ΣΟΥΒΛΑΚΙ");
    }

    @Test
    void pdfAc6_nonMemberIsRejected() {
        assertThatThrownBy(() -> exportService.exportPdf(groupId, carolId))
                .isInstanceOf(GroupAccessDeniedException.class);
    }

    private void recordGroceriesAndDinner() {
        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("30.00"), "Groceries", aliceId, List.of(aliceId, bobId, carolId), SEPT_1), aliceId);
        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("20.00"), "Dinner", bobId, List.of(aliceId, bobId), SEPT_5, "USD"), aliceId);
    }

    private static String pdfText(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    // Cells come out of the PDF in order, but the text extractor only sometimes puts whitespace
    // between neighbouring cells, and a long cell may wrap onto another line.
    private static Pattern row(String... cells) {
        return Pattern.compile(String.join("\\s*", cells).replace(" ", "\\s+"));
    }

    private static String csvText(byte[] csv) {
        return new String(csv, StandardCharsets.UTF_8);
    }
}

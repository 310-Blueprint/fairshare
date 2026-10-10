package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.MemberBalance;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.ExpenseShare;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
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

    // Bundled with OpenPDF. Embedded so accents, symbols and non-Latin scripts render, which the
    // built-in PDF fonts cannot do beyond Western European characters.
    private static final String UNICODE_FONT = "font-fallback/LiberationSans-Regular.ttf";
    private static final Color HEADER_BACKGROUND = new Color(230, 230, 230);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ExpenseGroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final ExpenseGroupService groupService;
    private final Clock clock;

    public GroupExportService(ExpenseGroupRepository groupRepository, ExpenseRepository expenseRepository,
                              ExpenseShareRepository expenseShareRepository, ExpenseGroupService groupService,
                              Clock clock) {
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.groupService = groupService;
        this.clock = clock;
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
        for (Expense expense : expensesOldestFirst(groupId)) {
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

    /**
     * AC2: the expense list followed by each current member's total paid, total share and net
     * balance. A group with no expenses still lists every member, at zero (AC4).
     */
    @Transactional(readOnly = true)
    public byte[] exportPdf(Long groupId, Long currentUserId) {
        ExpenseGroup group = requireMemberGroup(groupId, currentUserId); // AC6
        String currency = group.getBaseCurrency().name();
        List<Expense> expenses = expensesOldestFirst(groupId);
        Map<Long, List<ExpenseShare>> sharesByExpense = sharesByExpense(groupId);

        // The same calculation as GET /groups/{id}/balances, so the two never disagree.
        Map<Long, BigDecimal> netBalances = groupService.getBalances(groupId, currentUserId).stream()
                .collect(Collectors.toMap(MemberBalance::userId, MemberBalance::balance));
        Map<Long, BigDecimal> totalPaid = expenses.stream()
                .collect(Collectors.groupingBy(expense -> expense.getPaidBy().getId(),
                        Collectors.reducing(ZERO, Expense::getAmount, BigDecimal::add)));
        Map<Long, BigDecimal> totalShare = sharesByExpense.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(share -> share.getUser().getId(),
                        Collectors.reducing(ZERO, ExpenseShare::getShareAmount, BigDecimal::add)));

        BaseFont baseFont = loadUnicodeFont();
        Font titleFont = new Font(baseFont, 16, Font.BOLD);
        Font headingFont = new Font(baseFont, 12, Font.BOLD);
        Font bodyFont = new Font(baseFont, 9);
        Font headerCellFont = new Font(baseFont, 9, Font.BOLD);
        Font noteFont = new Font(baseFont, 8, Font.ITALIC, Color.DARK_GRAY);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Landscape so the split column has room on a page.
        try (Document document = new Document(PageSize.A4.rotate())) {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph(group.getGroupName(), titleFont));
            if (group.getDescription() != null && !group.getDescription().isBlank()) {
                document.add(new Paragraph(group.getDescription(), bodyFont));
            }
            document.add(new Paragraph(
                    "Exported " + LocalDate.now(clock) + ". All amounts in " + currency + ".", bodyFont));

            document.add(sectionHeading("Expenses", headingFont));
            if (expenses.isEmpty()) {
                document.add(new Paragraph("No expenses recorded.", bodyFont));
            } else {
                PdfPTable table = table(new float[] {1.2f, 3f, 1.4f, 1.2f, 3.6f},
                        List.of("Date", "Description", "Amount", "Paid By", "Split"), headerCellFont);
                for (Expense expense : expenses) {
                    table.addCell(cell(expense.getExpenseDate().toString(), bodyFont, Element.ALIGN_LEFT));
                    table.addCell(cell(expense.getDescription(), bodyFont, Element.ALIGN_LEFT));
                    table.addCell(cell(amountWithOriginal(expense, currency), bodyFont, Element.ALIGN_RIGHT));
                    table.addCell(cell(expense.getPaidBy().getUsername(), bodyFont, Element.ALIGN_LEFT));
                    table.addCell(cell(splitSummary(sharesByExpense.getOrDefault(expense.getId(), List.of())),
                            bodyFont, Element.ALIGN_LEFT));
                }
                document.add(table);
            }

            document.add(sectionHeading("Member balances", headingFont));
            PdfPTable balances = table(new float[] {3f, 1.5f, 1.5f, 1.5f},
                    List.of("Member", "Total Paid", "Total Share", "Net Balance"), headerCellFont);
            for (User member : membersByUsername(group)) {
                balances.addCell(cell(member.getUsername(), bodyFont, Element.ALIGN_LEFT));
                balances.addCell(cell(money(totalPaid.get(member.getId())), bodyFont, Element.ALIGN_RIGHT));
                balances.addCell(cell(money(totalShare.get(member.getId())), bodyFont, Element.ALIGN_RIGHT));
                balances.addCell(cell(money(netBalances.get(member.getId())), bodyFont, Element.ALIGN_RIGHT));
            }
            document.add(balances);
            document.add(new Paragraph(
                    "A positive net balance means the member owes money, a negative one means they are "
                            + "owed. Net balance includes settlement payments marked as paid, so it can differ "
                            + "from total share minus total paid.", noteFont));
        }
        return out.toByteArray();
    }

    private ExpenseGroup requireMemberGroup(Long groupId, Long currentUserId) {
        return groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);
    }

    private List<Expense> expensesOldestFirst(Long groupId) {
        return expenseRepository.findByGroupIdOrderByExpenseDateAscIdAsc(groupId);
    }

    private Map<Long, List<ExpenseShare>> sharesByExpense(Long groupId) {
        return expenseShareRepository.findByExpenseGroupId(groupId).stream()
                .collect(Collectors.groupingBy(share -> share.getExpense().getId()));
    }

    // Matches the order of the group's member list.
    private List<User> membersByUsername(ExpenseGroup group) {
        return group.getMembers().stream()
                .map(UserInGroup::getUser)
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // e.g. "alice: 10.00; bob: 10.00", in user id order to match how leftover cents are allocated.
    private String splitSummary(List<ExpenseShare> shares) {
        return shares.stream()
                .sorted(Comparator.comparing(share -> share.getUser().getId()))
                .map(share -> share.getUser().getUsername() + ": " + share.getShareAmount().toPlainString())
                .collect(Collectors.joining("; "));
    }

    // e.g. "34.11 (USD 20.00)" for an expense entered in another currency.
    private String amountWithOriginal(Expense expense, String currency) {
        String amount = expense.getAmount().toPlainString();
        if (currency.equals(expense.getOriginalCurrency())) {
            return amount;
        }
        return amount + " (" + expense.getOriginalCurrency() + " " + expense.getOriginalAmount().toPlainString() + ")";
    }

    private String money(BigDecimal value) {
        return (value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP)).toPlainString();
    }

    private Paragraph sectionHeading(String text, Font font) {
        Paragraph heading = new Paragraph(text, font);
        heading.setSpacingBefore(14);
        heading.setSpacingAfter(6);
        return heading;
    }

    private PdfPTable table(float[] widths, List<String> headers, Font font) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1); // repeats the header when the table runs onto another page
        for (String header : headers) {
            PdfPCell cell = cell(header, font, Element.ALIGN_LEFT);
            cell.setBackgroundColor(HEADER_BACKGROUND);
            table.addCell(cell);
        }
        return table;
    }

    private PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4);
        return cell;
    }

    private static BaseFont loadUnicodeFont() {
        try (InputStream font = GroupExportService.class.getClassLoader().getResourceAsStream(UNICODE_FONT)) {
            if (font == null) {
                throw new IllegalStateException("PDF font not found on the classpath: " + UNICODE_FONT);
            }
            return BaseFont.createFont(UNICODE_FONT, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false,
                    font.readAllBytes(), null);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load the PDF font", e);
        }
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

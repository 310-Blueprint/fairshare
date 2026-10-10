package nz.ac.auckland.se310.fairshare.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** #12 AC5: how a single CSV cell is escaped so Excel reads it back unchanged. */
class GroupExportServiceCsvCellTest {

    @Test
    void plainTextIsLeftAlone() {
        assertThat(GroupExportService.csvCell("Groceries")).isEqualTo("Groceries");
        assertThat(GroupExportService.csvCell("Café €12")).isEqualTo("Café €12");
    }

    @Test
    void nullIsAnEmptyCell() {
        assertThat(GroupExportService.csvCell(null)).isEmpty();
    }

    @Test
    void commaIsQuoted() {
        assertThat(GroupExportService.csvCell("Milk, eggs")).isEqualTo("\"Milk, eggs\"");
    }

    @Test
    void quotesAreDoubledInsideQuotes() {
        assertThat(GroupExportService.csvCell("The \"good\" pizza")).isEqualTo("\"The \"\"good\"\" pizza\"");
    }

    @Test
    void lineBreaksAreQuoted() {
        assertThat(GroupExportService.csvCell("Line one\nLine two")).isEqualTo("\"Line one\nLine two\"");
        assertThat(GroupExportService.csvCell("Line one\r\nLine two")).isEqualTo("\"Line one\r\nLine two\"");
    }

    @ParameterizedTest
    // The default quote character is an apostrophe, which the expected values start with.
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "=1+2|'=1+2",
            "+64 21 555|'+64 21 555",
            "-5 refund|'-5 refund",
            "@SUM(A1)|'@SUM(A1)"})
    void formulaLikeTextIsPrefixedSoExcelShowsItAsText(String value, String expected) {
        assertThat(GroupExportService.csvCell(value)).isEqualTo(expected);
    }

    @Test
    void formulaGuardAndQuotingCombine() {
        assertThat(GroupExportService.csvCell("=HYPERLINK(\"x\",\"y\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"x\"\",\"\"y\"\")\"");
    }
}

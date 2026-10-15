package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.exception.InvalidExportFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** #12: parsing the ?format= parameter of the group export endpoint. */
class ExportFormatTest {

    @ParameterizedTest
    @ValueSource(strings = {"csv", "CSV", " Csv "})
    void parsesCsvCaseInsensitively(String value) {
        assertThat(ExportFormat.fromParameter(value)).isEqualTo(ExportFormat.CSV);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "PDF"})
    void parsesPdfCaseInsensitively(String value) {
        assertThat(ExportFormat.fromParameter(value)).isEqualTo(ExportFormat.PDF);
    }

    @Test
    void csvIsUtf8TextCsv() {
        assertThat(ExportFormat.CSV.mediaType()).hasToString("text/csv;charset=UTF-8");
        assertThat(ExportFormat.CSV.extension()).isEqualTo("csv");
    }

    @Test
    void pdfIsApplicationPdf() {
        assertThat(ExportFormat.PDF.mediaType()).hasToString("application/pdf");
        assertThat(ExportFormat.PDF.extension()).isEqualTo("pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "json", "csvx"})
    void rejectsUnknownFormat(String value) {
        assertThatThrownBy(() -> ExportFormat.fromParameter(value))
                .isInstanceOf(InvalidExportFormatException.class)
                .hasMessage("Unsupported export format: " + value + ". Use csv or pdf");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingFormat(String value) {
        assertThatThrownBy(() -> ExportFormat.fromParameter(value))
                .isInstanceOf(InvalidExportFormatException.class)
                .hasMessage("An export format is required: csv or pdf");
    }
}

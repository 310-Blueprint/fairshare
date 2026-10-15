package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.exception.ReceiptExtractionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptTextParserTest {

    private final ReceiptTextParser parser = new ReceiptTextParser();

    @Test
    void extractsItemsAndReceiptTotal() {
        ReceiptExtractionResponse result = parser.parse("""
                CORNER STORE
                Milk                 4.50
                Bread                3.20
                Loyalty savings      0.20
                SUBTOTAL             7.70
                TOTAL                7.70
                EFTPOS               7.70
                """);

        assertThat(result.items()).extracting(item -> item.description())
                .containsExactly("Milk", "Bread");
        assertThat(result.items()).extracting(item -> item.price().toPlainString())
                .containsExactly("4.50", "3.20");
        assertThat(result.total().toPlainString()).isEqualTo("7.70");
    }

    @Test
    void usesTheItemSumWhenNoTotalWasRecognised() {
        ReceiptExtractionResponse result = parser.parse("""
                MARKET
                Apples 2.40
                Bananas 3.10
                """);

        assertThat(result.total().toPlainString()).isEqualTo("5.50");
    }

    @Test
    void rejectsTextWithoutPricedItems() {
        assertThatThrownBy(() -> parser.parse("THANK YOU"))
                .isInstanceOf(ReceiptExtractionException.class)
                .hasMessageContaining("clearer image")
                .hasMessageContaining("manually");
    }
}

package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.exception.ReceiptExtractionException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TesseractReceiptOcrClientTest {

    private final ReceiptTextParser parser = new ReceiptTextParser();

    @Test
    void preprocessesTheImageAndParsesRecognisedText() throws Exception {
        TesseractReceiptOcrClient client = new TesseractReceiptOcrClient(
                parser,
                image -> {
                    assertThat(image.getType()).isEqualTo(BufferedImage.TYPE_BYTE_BINARY);
                    assertThat(image.getWidth()).isEqualTo(1400);
                    return "Corner Store\nMilk 4.50\nTOTAL 4.50";
                });

        ReceiptExtractionResponse result = client.extract(pngImage(), "image/png");

        assertThat(result.items()).hasSize(1);
        assertThat(result.total().toPlainString()).isEqualTo("4.50");
    }

    @Test
    void rejectsBytesThatCannotBeDecodedAsAnImage() {
        TesseractReceiptOcrClient client = new TesseractReceiptOcrClient(
                parser, image -> "unused");

        assertThatThrownBy(() -> client.extract(new byte[] {1, 2, 3}, "image/png"))
                .isInstanceOf(ReceiptExtractionException.class)
                .hasMessageContaining("clearer image")
                .hasMessageContaining("manually");
    }

    @Test
    void bundledTesseractRecognisesACleanReceipt() throws Exception {
        BufferedImage receipt = new BufferedImage(1200, 520, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = receipt.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, receipt.getWidth(), receipt.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 54));
            graphics.drawString("CORNER STORE", 60, 90);
            graphics.drawString("Milk                 4.50", 60, 190);
            graphics.drawString("Bread                3.20", 60, 290);
            graphics.drawString("TOTAL                7.70", 60, 410);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(receipt, "png", output);

        ReceiptExtractionResponse result = new TesseractReceiptOcrClient(parser)
                .extract(output.toByteArray(), "image/png");

        assertThat(result.items()).hasSize(2);
        assertThat(result.total().toPlainString()).isEqualTo("7.70");
    }

    private byte[] pngImage() throws Exception {
        BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}

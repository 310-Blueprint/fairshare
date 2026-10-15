package nz.ac.auckland.se310.fairshare.service;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptImagePreprocessorTest {

    private final ReceiptImagePreprocessor preprocessor = new ReceiptImagePreprocessor();

    @Test
    void rescalesSmallImagesToAnOcrFriendlyEquivalentOfThreeHundredDpi() {
        BufferedImage source = new BufferedImage(700, 350, BufferedImage.TYPE_INT_RGB);

        BufferedImage result = preprocessor.rescaleForOcr(source);

        assertThat(result.getWidth()).isEqualTo(1400);
        assertThat(result.getHeight()).isEqualTo(700);
    }

    @Test
    void convertsColourToGrayscaleAndBinarizesIt() {
        BufferedImage source = new BufferedImage(4, 1, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, Color.BLACK.getRGB());
        source.setRGB(1, 0, new Color(40, 40, 40).getRGB());
        source.setRGB(2, 0, new Color(220, 220, 220).getRGB());
        source.setRGB(3, 0, Color.WHITE.getRGB());

        BufferedImage grayscale = preprocessor.toGrayscale(source);
        BufferedImage binary = preprocessor.binarize(grayscale);

        assertThat(grayscale.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
        assertThat(binary.getType()).isEqualTo(BufferedImage.TYPE_BYTE_BINARY);
        assertThat(binary.getRaster().getSample(0, 0, 0)).isZero();
        assertThat(binary.getRaster().getSample(3, 0, 0)).isEqualTo(1);
    }

    @Test
    void medianFilterRemovesIsolatedNoise() {
        BufferedImage noisy = grayImage(7, 7, 255);
        noisy.getRaster().setSample(3, 3, 0, 0);

        BufferedImage result = preprocessor.medianDenoise(noisy);

        assertThat(result.getRaster().getSample(3, 3, 0)).isEqualTo(255);
    }

    @Test
    void isolatesTheBrightReceiptFromADarkBackground() {
        BufferedImage photo = grayImage(200, 140, 25);
        Graphics2D graphics = photo.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillPolygon(new Polygon(
                    new int[] {20, 180, 165, 10},
                    new int[] {0, 0, 139, 139},
                    4));
            graphics.setColor(Color.BLACK);
            graphics.fillRect(45, 50, 90, 4);
        } finally {
            graphics.dispose();
        }

        BufferedImage result = preprocessor.isolateReceiptRegion(photo);

        assertThat(result.getWidth()).isLessThan(photo.getWidth());
        assertThat(result.getRaster().getSample(result.getWidth() - 1, 100, 0))
                .isEqualTo(255);
        assertThat(result.getRaster().getSample(result.getWidth() / 2, 52, 0))
                .isLessThan(80);
    }

    @Test
    void adaptiveThresholdingKeepsTextAcrossUnevenLighting() {
        BufferedImage uneven = new BufferedImage(200, 60, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < uneven.getHeight(); y++) {
            for (int x = 0; x < uneven.getWidth(); x++) {
                int background = 70 + x * 160 / uneven.getWidth();
                int value = y >= 20 && y <= 26 ? background - 40 : background;
                uneven.getRaster().setSample(x, y, 0, value);
            }
        }

        BufferedImage result = preprocessor.binarize(uneven);

        assertThat(result.getRaster().getSample(20, 10, 0)).isEqualTo(1);
        assertThat(result.getRaster().getSample(20, 23, 0)).isZero();
        assertThat(result.getRaster().getSample(180, 10, 0)).isEqualTo(1);
        assertThat(result.getRaster().getSample(180, 23, 0)).isZero();
    }

    @Test
    void removesTinyBinarySpecksButKeepsLargerTextComponents() {
        BufferedImage binary = binaryImage(20, 20);
        Graphics2D graphics = binary.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(3, 3, 2, 2);
            graphics.fillRect(11, 11, 3, 3);
        } finally {
            graphics.dispose();
        }

        BufferedImage result = preprocessor.despeckle(binary);

        assertThat(result.getRaster().getSample(3, 3, 0)).isEqualTo(1);
        assertThat(result.getRaster().getSample(12, 12, 0)).isZero();
    }

    @Test
    void removesDiagonallyConnectedTinySpecksAsOneComponent() {
        BufferedImage binary = binaryImage(10, 10);
        binary.getRaster().setSample(2, 2, 0, 0);
        binary.getRaster().setSample(3, 3, 0, 0);

        BufferedImage result = preprocessor.despeckle(binary);

        assertThat(result.getRaster().getSample(2, 2, 0)).isEqualTo(1);
        assertThat(result.getRaster().getSample(3, 3, 0)).isEqualTo(1);
    }

    @Test
    void estimatesAndCorrectsTextLineSkew() {
        BufferedImage skewed = binaryImage(320, 180);
        Graphics2D graphics = skewed.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            for (int y = 30; y <= 130; y += 20) {
                graphics.drawLine(20, y, 300, y + 25);
                graphics.drawLine(20, y + 1, 300, y + 26);
            }
        } finally {
            graphics.dispose();
        }

        double detectedAngle = preprocessor.estimateSkewAngle(skewed);
        BufferedImage corrected = preprocessor.deskew(skewed);

        assertThat(detectedAngle).isBetween(4.0, 6.0);
        assertThat(Math.abs(preprocessor.estimateSkewAngle(corrected))).isLessThanOrEqualTo(1.0);
    }

    @Test
    void dilationAndErosionCloseSmallBreaksInText() {
        BufferedImage broken = binaryImage(12, 9);
        Graphics2D graphics = broken.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(2, 2, 3, 5);
            graphics.fillRect(6, 2, 3, 5);
        } finally {
            graphics.dispose();
        }

        BufferedImage result = preprocessor.closeSmallGaps(broken);

        assertThat(result.getRaster().getSample(5, 4, 0)).isZero();
    }

    @Test
    void removesDarkArtifactsConnectedToTheImageBorder() {
        BufferedImage bordered = binaryImage(20, 20);
        Graphics2D graphics = bordered.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, 2, 20);
            graphics.fillRect(8, 8, 4, 4);
        } finally {
            graphics.dispose();
        }

        BufferedImage result = preprocessor.removeBorderArtifacts(bordered);

        assertThat(result.getRaster().getSample(0, 10, 0)).isEqualTo(1);
        assertThat(result.getRaster().getSample(9, 9, 0)).isZero();
    }

    private BufferedImage grayImage(int width, int height, int value) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(value, value, value));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage binaryImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}

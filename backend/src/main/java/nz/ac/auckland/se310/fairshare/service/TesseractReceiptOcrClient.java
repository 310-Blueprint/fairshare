package nz.ac.auckland.se310.fairshare.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;
import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.exception.ReceiptExtractionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

@Component
public class TesseractReceiptOcrClient implements ReceiptOcrClient {

    private static final String EXTRACTION_FAILURE =
            "We couldn't read this receipt. Try a clearer image or enter the expense manually.";

    private final ReceiptTextParser parser;
    private final ReceiptImagePreprocessor preprocessor;
    private final TextExtractor textExtractor;

    @Autowired
    public TesseractReceiptOcrClient(
            ReceiptTextParser parser, ReceiptImagePreprocessor preprocessor) {
        this(parser, preprocessor, TesseractReceiptOcrClient::runTesseract);
    }

    public TesseractReceiptOcrClient(ReceiptTextParser parser) {
        this(parser, new ReceiptImagePreprocessor(), TesseractReceiptOcrClient::runTesseract);
    }

    TesseractReceiptOcrClient(ReceiptTextParser parser, TextExtractor textExtractor) {
        this(parser, new ReceiptImagePreprocessor(), textExtractor);
    }

    TesseractReceiptOcrClient(
            ReceiptTextParser parser,
            ReceiptImagePreprocessor preprocessor,
            TextExtractor textExtractor) {
        this.parser = parser;
        this.preprocessor = preprocessor;
        this.textExtractor = textExtractor;
    }

    @Override
    public ReceiptExtractionResponse extract(byte[] image, String mediaType) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image));
            if (decoded == null) {
                throw new ReceiptExtractionException(EXTRACTION_FAILURE);
            }
            return parser.parse(textExtractor.extract(preprocessor.preprocess(decoded)));
        } catch (ReceiptExtractionException ex) {
            throw ex;
        } catch (IOException | TesseractException | RuntimeException ex) {
            throw new ReceiptExtractionException(
                    "Receipt extraction failed. Enter the expense manually or try another image.",
                    ex);
        }
    }

    private static String runTesseract(BufferedImage image) throws TesseractException {
        File tessData = LoadLibs.extractTessResources("tessdata");
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessData.getAbsolutePath());
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(3);
        tesseract.setOcrEngineMode(1);
        tesseract.setVariable(
                "user_defined_dpi", String.valueOf(ReceiptImagePreprocessor.TARGET_DPI));
        return tesseract.doOCR(image);
    }

    @FunctionalInterface
    interface TextExtractor {
        String extract(BufferedImage image) throws TesseractException;
    }
}

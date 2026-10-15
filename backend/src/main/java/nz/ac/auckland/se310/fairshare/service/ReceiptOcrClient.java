package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;

public interface ReceiptOcrClient {

    ReceiptExtractionResponse extract(byte[] image, String mediaType);
}

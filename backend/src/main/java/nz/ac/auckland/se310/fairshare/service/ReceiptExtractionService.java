package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.InvalidReceiptException;
import nz.ac.auckland.se310.fairshare.exception.ReceiptExtractionException;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

@Service
public class ReceiptExtractionService {

    public static final String FILE_REQUIREMENTS =
            "Choose a JPG or PNG image no larger than 10 MB.";

    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "image/jpeg", "jpeg",
            "image/png", "png");

    private final ExpenseGroupRepository groupRepository;
    private final ReceiptOcrClient ocrClient;
    private final long maxFileSize;

    public ReceiptExtractionService(
            ExpenseGroupRepository groupRepository,
            ReceiptOcrClient ocrClient,
            @Value("${receipt.max-file-size-bytes:10485760}") long maxFileSize) {
        this.groupRepository = groupRepository;
        this.ocrClient = ocrClient;
        this.maxFileSize = maxFileSize;
    }

    @Transactional(readOnly = true)
    public ReceiptExtractionResponse extract(
            Long groupId, Long currentUserId, MultipartFile file) {
        groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);

        if (file == null || file.isEmpty() || file.getSize() > maxFileSize) {
            throw new InvalidReceiptException(FILE_REQUIREMENTS);
        }

        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_TYPES.containsKey(contentType.toLowerCase())) {
            throw new InvalidReceiptException(FILE_REQUIREMENTS);
        }

        try {
            byte[] bytes = file.getBytes();
            if (!hasExpectedSignature(bytes, contentType.toLowerCase())) {
                throw new InvalidReceiptException(FILE_REQUIREMENTS);
            }
            return ocrClient.extract(bytes, contentType.toLowerCase());
        } catch (InvalidReceiptException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ReceiptExtractionException(
                    "The receipt image could not be read. Enter the expense manually or try another image.",
                    ex);
        }
    }

    private boolean hasExpectedSignature(byte[] bytes, String contentType) {
        return switch (SUPPORTED_TYPES.get(contentType)) {
            case "jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case "png" -> bytes.length >= 8 && Arrays.equals(
                    Arrays.copyOf(bytes, 8),
                    new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            default -> false;
        };
    }
}

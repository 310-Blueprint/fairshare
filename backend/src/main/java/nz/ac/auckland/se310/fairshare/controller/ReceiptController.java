package nz.ac.auckland.se310.fairshare.controller;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.security.CurrentUserProvider;
import nz.ac.auckland.se310.fairshare.service.ReceiptExtractionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/groups/{groupId}/receipts")
public class ReceiptController {

    private final ReceiptExtractionService receiptExtractionService;
    private final CurrentUserProvider currentUser;

    public ReceiptController(
            ReceiptExtractionService receiptExtractionService,
            CurrentUserProvider currentUser) {
        this.receiptExtractionService = receiptExtractionService;
        this.currentUser = currentUser;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReceiptExtractionResponse extract(
            @PathVariable Long groupId,
            @RequestParam("file") MultipartFile file) {
        return receiptExtractionService.extract(groupId, currentUser.currentUserId(), file);
    }
}

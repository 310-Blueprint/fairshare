package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.controller.ReceiptController;
import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.dto.ReceiptItemResponse;
import nz.ac.auckland.se310.fairshare.exception.GlobalExceptionHandler;
import nz.ac.auckland.se310.fairshare.security.CurrentUserProvider;
import nz.ac.auckland.se310.fairshare.service.ReceiptExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReceiptControllerTest {

    @Test
    void uploadsAReceiptForTheAuthenticatedGroupMember() throws Exception {
        ReceiptExtractionService service = mock(ReceiptExtractionService.class);
        CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
        when(currentUser.currentUserId()).thenReturn(7L);
        when(service.extract(org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReceiptExtractionResponse(
                        List.of(new ReceiptItemResponse("Milk", new BigDecimal("4.50"))),
                        new BigDecimal("4.50")));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new ReceiptController(service, currentUser))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        mvc.perform(multipart("/groups/4/receipts/extract").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].description").value("Milk"))
                .andExpect(jsonPath("$.total").value(4.50));

        verify(service).extract(org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any());
    }
}

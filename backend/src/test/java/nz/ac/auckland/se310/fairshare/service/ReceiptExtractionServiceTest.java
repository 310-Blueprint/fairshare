package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.dto.ReceiptItemResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.InvalidReceiptException;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptExtractionServiceTest {

    @Mock ExpenseGroupRepository groupRepository;
    @Mock ReceiptOcrClient ocrClient;

    private ReceiptExtractionService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptExtractionService(groupRepository, ocrClient, 10 * 1024 * 1024);
    }

    @Test
    void extractsAValidReceiptForAGroupMember() {
        when(groupRepository.findByIdAndMembersUserId(4L, 7L))
                .thenReturn(Optional.of(mock(ExpenseGroup.class)));
        ReceiptExtractionResponse expected = new ReceiptExtractionResponse(
                List.of(new ReceiptItemResponse("Milk", new BigDecimal("4.50"))),
                new BigDecimal("4.50"));
        when(ocrClient.extract(any(byte[].class), eq("image/jpeg"))).thenReturn(expected);

        ReceiptExtractionResponse result = service.extract(4L, 7L, jpegFile());

        assertThat(result).isEqualTo(expected);
        verify(ocrClient).extract(any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void rejectsUnsupportedOrSpoofedFilesBeforeCallingOcr() {
        when(groupRepository.findByIdAndMembersUserId(4L, 7L))
                .thenReturn(Optional.of(mock(ExpenseGroup.class)));
        MockMultipartFile text = new MockMultipartFile(
                "file", "receipt.txt", "text/plain", "not a receipt".getBytes());
        MockMultipartFile spoofedPng = new MockMultipartFile(
                "file", "receipt.png", "image/png", "not really png".getBytes());

        assertThatThrownBy(() -> service.extract(4L, 7L, text))
                .isInstanceOf(InvalidReceiptException.class)
                .hasMessageContaining("JPG or PNG");
        assertThatThrownBy(() -> service.extract(4L, 7L, spoofedPng))
                .isInstanceOf(InvalidReceiptException.class)
                .hasMessageContaining("10 MB");
        verifyNoInteractions(ocrClient);
    }

    @Test
    void rejectsAnOversizedFile() {
        when(groupRepository.findByIdAndMembersUserId(4L, 7L))
                .thenReturn(Optional.of(mock(ExpenseGroup.class)));
        service = new ReceiptExtractionService(groupRepository, ocrClient, 3);

        assertThatThrownBy(() -> service.extract(4L, 7L, jpegFile()))
                .isInstanceOf(InvalidReceiptException.class);
        verifyNoInteractions(ocrClient);
    }

    @Test
    void rejectsUsersOutsideTheGroupBeforeReadingTheFile() {
        when(groupRepository.findByIdAndMembersUserId(4L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extract(4L, 7L, jpegFile()))
                .isInstanceOf(GroupAccessDeniedException.class);
        verifyNoInteractions(ocrClient);
    }

    private MockMultipartFile jpegFile() {
        return new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01});
    }
}

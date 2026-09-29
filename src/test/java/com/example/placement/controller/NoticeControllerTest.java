package com.example.placement.controller;

import com.example.placement.dto.NoticeRequest;
import com.example.placement.entity.Notice;
import com.example.placement.service.NoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeControllerTest {

    private final NoticeService noticeService = mock(NoticeService.class);
    private final NoticeController controller = new NoticeController(noticeService);
    private final AdminNoticeController adminController = new AdminNoticeController(noticeService);

    private NoticeRequest request;

    @BeforeEach
    void setUp() {
        request = new NoticeRequest();
        request.setTitle("Placement Drive 2026");
        request.setMessage("Registration closes on Friday.");
    }

    @Test
    void studentEndpointReturnsOnlyVisibleNotices() {
        Notice notice = new Notice();
        notice.setId("n1");
        when(noticeService.visibleNotices()).thenReturn(List.of(notice));

        assertThat(controller.visibleNotices()).extracting(Notice::getId).containsExactly("n1");
    }

    @Test
    void adminListReturnsEveryNoticeIncludingDisabled() {
        when(noticeService.allNotices()).thenReturn(List.of(new Notice(), new Notice()));

        assertThat(adminController.allNotices()).hasSize(2);
    }

    @Test
    void createReturnsCreatedStatus() {
        when(noticeService.create(any(NoticeRequest.class))).thenAnswer(invocation -> {
            Notice saved = new Notice();
            saved.setId("n1");
            return saved;
        });

        var response = adminController.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isEqualTo("n1");
    }

    @Test
    void updateDelegatesIdAndRequest() {
        Notice existing = new Notice();
        existing.setId("n1");
        when(noticeService.update(eq("n1"), any(NoticeRequest.class))).thenReturn(existing);

        assertThat(adminController.update("n1", request).getId()).isEqualTo("n1");
    }

    @Test
    void statusPatchDefaultsToActiveWhenBodyIsAbsent() {
        Notice existing = new Notice();
        existing.setId("n1");
        when(noticeService.setActive("n1", true)).thenReturn(existing);

        assertThat(adminController.setStatus("n1", null).getId()).isEqualTo("n1");
        verify(noticeService).setActive("n1", true);
    }

    @Test
    void statusPatchTogglesActiveFlag() {
        Notice existing = new Notice();
        existing.setId("n1");
        existing.setActive(false);
        when(noticeService.setActive("n1", false)).thenReturn(existing);

        assertThat(adminController.setStatus("n1", Map.of("active", false)).isActive()).isFalse();
        verify(noticeService).setActive("n1", false);
    }

    @Test
    void deleteReturnsConfirmationMessage() {
        Map<String, String> body = adminController.delete("n1");

        assertThat(body.get("message")).isEqualTo("Notice deleted");
        verify(noticeService).delete("n1");
    }

    @Test
    void missingNoticeSurfacesAsNotFound() {
        when(noticeService.get("missing")).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found"));

        assertThatThrownBy(() -> adminController.getNotice("missing"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void singleNoticeLookupUsesDirectRepositoryHit() {
        Notice notice = new Notice();
        notice.setId("n1");
        notice.setExpiresAt(LocalDateTime.now().plusDays(2));
        when(noticeService.get("n1")).thenReturn(notice);

        assertThat(adminController.getNotice("n1").getId()).isEqualTo("n1");
        verify(noticeService, never()).allNotices();
    }
}

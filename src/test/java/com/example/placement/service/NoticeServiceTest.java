package com.example.placement.service;

import com.example.placement.dto.NoticeRequest;
import com.example.placement.entity.Notice;
import com.example.placement.repository.NoticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeServiceTest {

    private final NoticeRepository noticeRepository = mock(NoticeRepository.class);
    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(noticeRepository);
    }

    private NoticeRequest request() {
        NoticeRequest request = new NoticeRequest();
        request.setTitle("Placement Drive 2026");
        request.setMessage("Registration closes on Friday.");
        return request;
    }

    @Test
    void createTrimsFieldsAndDefaultsToActiveWithoutExpiry() {
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NoticeRequest request = request();
        request.setTitle("  Placement Drive 2026  ");
        request.setCategory("  Placement  ");
        request.setLink("  https://example.com/registration  ");

        Notice created = noticeService.create(request);

        assertThat(created.getTitle()).isEqualTo("Placement Drive 2026");
        assertThat(created.getCategory()).isEqualTo("Placement");
        assertThat(created.getLink()).isEqualTo("https://example.com/registration");
        assertThat(created.isActive()).isTrue();
        assertThat(created.getExpiresAt()).isNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
    }

    @Test
    void createTreatsBlankOptionalFieldsAsNull() {
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NoticeRequest request = request();
        request.setLink("   ");
        request.setCategory("");

        Notice created = noticeService.create(request);

        assertThat(created.getLink()).isNull();
        assertThat(created.getCategory()).isNull();
    }

    @Test
    void createRejectsNonHttpLinks() {
        assertThatThrownBy(() -> noticeService.create(requestWithLink("javascript:alert(1)")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("http://");
        assertThatThrownBy(() -> noticeService.create(requestWithLink("data:text/html,<script>")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> noticeService.create(requestWithLink("/relative/path")))
                .isInstanceOf(ResponseStatusException.class);
        verify(noticeRepository, never()).save(any());
    }

    @Test
    void createAcceptsAbsoluteHttpAndHttpsLinks() {
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(noticeService.create(requestWithLink("http://example.com/a")).getLink())
                .isEqualTo("http://example.com/a");
        assertThat(noticeService.create(requestWithLink("https://example.com/a")).getLink())
                .isEqualTo("https://example.com/a");
    }

    @Test
    void createRejectsExpiryInThePast() {
        assertThatThrownBy(() -> noticeService.create(requestWithExpiry(LocalDateTime.now().minusDays(1))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("future");
    }

    @Test
    void updateOverwritesEditableFieldsAndStampsUpdatedAt() {
        Notice existing = new Notice();
        existing.setId("n1");
        existing.setTitle("Old");
        existing.setActive(true);
        when(noticeRepository.findById("n1")).thenReturn(Optional.of(existing));
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NoticeRequest update = request();
        update.setActive(false);

        Notice updated = noticeService.update("n1", update);

        assertThat(updated.getId()).isEqualTo("n1");
        assertThat(updated.getTitle()).isEqualTo("Placement Drive 2026");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateAndSetActiveFailWithNotFoundForUnknownId() {
        when(noticeRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.update("missing", request()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> noticeService.setActive("missing", true))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteRemovesExistingNotice() {
        when(noticeRepository.existsById("n1")).thenReturn(true);

        noticeService.delete("n1");

        verify(noticeRepository).deleteById("n1");
    }

    @Test
    void deleteFailsWhenNoticeIsMissing() {
        when(noticeRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> noticeService.delete("missing"))
                .isInstanceOf(ResponseStatusException.class);
        verify(noticeRepository, never()).deleteById(any());
    }

    @Test
    void setActiveFlipsFlagAndStampsUpdatedAt() {
        Notice existing = new Notice();
        existing.setId("n1");
        existing.setActive(false);
        when(noticeRepository.findById("n1")).thenReturn(Optional.of(existing));
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Notice updated = noticeService.setActive("n1", true);

        assertThat(updated.isActive()).isTrue();
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void visibleNoticesQueryExcludesInactiveAndExpired() {
        Notice active = new Notice();
        active.setId("a");
        when(noticeRepository.findVisible(any(LocalDateTime.class))).thenReturn(List.of(active));

        assertThat(noticeService.visibleNotices()).extracting(Notice::getId).containsExactly("a");
        verify(noticeRepository).findVisible(any(LocalDateTime.class));
    }

    private NoticeRequest requestWithLink(String link) {
        NoticeRequest request = request();
        request.setLink(link);
        return request;
    }

    private NoticeRequest requestWithExpiry(LocalDateTime expiry) {
        NoticeRequest request = request();
        request.setExpiresAt(expiry);
        return request;
    }

    @Test
    void updateKeepsANoticeDisabledWhenActiveIsOmitted() {
        when(noticeRepository.findById("n1")).thenReturn(Optional.of(disabled()));
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Notice updated = noticeService.update("n1", request());

        assertThat(updated.isActive())
                .as("an update that omits 'active' must not re-publish a disabled notice")
                .isFalse();
    }

    @Test
    void updateStillHonoursAnExplicitActiveFlag() {
        when(noticeRepository.findById("n1")).thenReturn(Optional.of(disabled()));
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NoticeRequest request = request();
        request.setActive(true);

        assertThat(noticeService.update("n1", request).isActive()).isTrue();
    }

    private Notice disabled() {
        Notice notice = new Notice();
        notice.setId("n1");
        notice.setTitle("Old title");
        notice.setMessage("Old message");
        notice.setActive(false);
        return notice;
    }
}

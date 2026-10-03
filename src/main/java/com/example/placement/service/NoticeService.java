package com.example.placement.service;

import com.example.placement.dto.NoticeRequest;
import com.example.placement.entity.Notice;
import com.example.placement.repository.NoticeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    public List<Notice> visibleNotices() {
        return noticeRepository.findVisible(LocalDateTime.now());
    }

    public List<Notice> allNotices() {
        return noticeRepository.findAllByOrderByUpdatedAtDesc();
    }

    public Notice get(String id) {
        return require(id);
    }

    public Notice create(NoticeRequest request) {
        Notice notice = new Notice();
        notice.setCreatedAt(LocalDateTime.now());
        apply(notice, request, true);
        return noticeRepository.save(notice);
    }

    public Notice update(String id, NoticeRequest request) {
        Notice existing = require(id);
        apply(existing, request, false);
        return noticeRepository.save(existing);
    }

    public Notice setActive(String id, boolean active) {
        Notice existing = require(id);
        existing.setActive(active);
        existing.setUpdatedAt(LocalDateTime.now());
        return noticeRepository.save(existing);
    }

    public void delete(String id) {
        if (!noticeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found");
        }
        noticeRepository.deleteById(id);
    }

    private Notice require(String id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found"));
    }

    private void apply(Notice notice, NoticeRequest request, boolean creating) {
        notice.setTitle(request.getTitle().trim());
        notice.setMessage(request.getMessage().trim());
        notice.setLink(normalizeLink(request.getLink()));
        notice.setCategory(blankToNull(request.getCategory()));
        if (creating) {
            notice.setActive(request.getActive() == null ? Boolean.TRUE : request.getActive());
        } else if (request.getActive() != null) {
            // Omitting "active" on an update must not silently re-publish a disabled notice.
            notice.setActive(request.getActive());
        }
        notice.setExpiresAt(request.getExpiresAt());
        notice.setUpdatedAt(LocalDateTime.now());
        if (creating && notice.getCreatedAt() == null) {
            notice.setCreatedAt(LocalDateTime.now());
        }
        if (notice.getExpiresAt() != null && notice.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expiration date must be in the future");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Mirrors the resume-URL check in UserController: only absolute http/https URLs with a
     * host are accepted, so a notice link cannot smuggle javascript: or data: into the page.
     */
    private String normalizeLink(String value) {
        String link = blankToNull(value);
        if (link == null) {
            return null;
        }
        try {
            URI uri = URI.create(link);
            boolean allowed = uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Link must be a full http:// or https:// URL");
            }
            return link;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Link must be a full http:// or https:// URL");
        }
    }
}

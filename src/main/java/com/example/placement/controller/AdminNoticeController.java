package com.example.placement.controller;

import com.example.placement.dto.NoticeRequest;
import com.example.placement.entity.Notice;
import com.example.placement.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Admin notice management. Every path is under /api/admin/** and is restricted to
 * ROLE_ADMIN in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/notices")
public class AdminNoticeController {

    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public List<Notice> allNotices() {
        return noticeService.allNotices();
    }

    @GetMapping("/{id}")
    public Notice getNotice(@PathVariable String id) {
        return noticeService.get(id);
    }

    @PostMapping
    public ResponseEntity<Notice> create(@Valid @RequestBody NoticeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noticeService.create(request));
    }

    @PutMapping("/{id}")
    public Notice update(@PathVariable String id, @Valid @RequestBody NoticeRequest request) {
        return noticeService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public Notice setStatus(@PathVariable String id, @RequestBody(required = false) Map<String, Boolean> body) {
        // An absent or null "active" used to default to true, so a malformed request
        // published a notice to every student instead of being rejected.
        if (body == null || body.get("active") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An 'active' boolean is required");
        }
        return noticeService.setActive(id, body.get("active"));
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable String id) {
        noticeService.delete(id);
        return Map.of("message", "Notice deleted");
    }
}

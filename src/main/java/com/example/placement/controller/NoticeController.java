package com.example.placement.controller;

import com.example.placement.entity.Notice;
import com.example.placement.service.NoticeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only endpoint for the student dashboard. Returns only enabled, unexpired
 * notices; admin CRUD lives in {@link AdminNoticeController}.
 */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public List<Notice> visibleNotices() {
        return noticeService.visibleNotices();
    }
}

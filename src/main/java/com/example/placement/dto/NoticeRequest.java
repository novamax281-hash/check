package com.example.placement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 120, message = "Title must be at most 120 characters")
    private String title;

    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message must be at most 2000 characters")
    private String message;

    @Size(max = 500, message = "Link must be at most 500 characters")
    private String link;

    @Size(max = 40, message = "Category must be at most 40 characters")
    private String category;

    private Boolean active;

    private LocalDateTime expiresAt;
}

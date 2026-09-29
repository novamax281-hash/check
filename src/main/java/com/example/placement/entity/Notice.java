package com.example.placement.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notices")
public class Notice {

    @Id
    private String id;

    private String title;

    private String message;

    /** Optional external link. Stored only after scheme/host validation. */
    private String link;

    @Indexed
    private String category;

    @Indexed
    private boolean active = true;

    @Indexed
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

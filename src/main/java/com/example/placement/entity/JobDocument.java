package com.example.placement.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobDocument {

    private String fileName;

    private String fileUrl;

    private String publicId;

    private String resourceType;

    private long size;

    private LocalDateTime uploadedAt;
}
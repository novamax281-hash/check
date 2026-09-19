package com.example.placement.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Metadata for a file stored on Cloudinary that is attached to a placement (job poster, JD PDF, etc.).
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
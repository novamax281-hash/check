package com.example.placement.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.placement.entity.JobDocument;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final boolean configured;

    public CloudinaryService(@Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
                             @Value("${CLOUDINARY_API_KEY:}") String apiKey,
                             @Value("${CLOUDINARY_API_SECRET:}") String apiSecret) {
        boolean ok = !isBlank(cloudName) && !isBlank(apiKey) && !isBlank(apiSecret);
        this.configured = ok;
        if (ok) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret));
        } else {
            this.cloudinary = null;
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    public JobDocument upload(MultipartFile file, String jobId) {
        requireConfigured();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "auto",
                            "public_id", UUID.randomUUID().toString().replace("-", ""),
                            "folder", "placement-portal/" + jobId));
            String fileName = safeFileName(file.getOriginalFilename());
            return new JobDocument(
                    fileName,
                    (String) uploadResult.get("secure_url"),
                    (String) uploadResult.get("public_id"),
                    (String) uploadResult.get("resource_type"),
                    file.getSize(),
                    LocalDateTime.now());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    public void delete(String publicId) {
        if (!configured) {
            return;
        }
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception ignored) {
            // Deletion on the file host failing is not critical; metadata removal already happened.
        }
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("Cloudinary is not configured. Set CLOUDINARY_CLOUD_NAME, "
                    + "CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET environment variables.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "document";
        }
        return original.replaceAll("[^a-zA-Z0-9._()-]", "_");
    }
}
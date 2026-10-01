package com.example.placement.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/** Payload for emailing a filtered applications CSV export as an attachment. */
@Data
public class ApplicationEmailRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Must be a valid email address")
    private String recipientEmail;

    private String subject;

    private String fileName;

    private String scope;

    private List<String> applicationIds;

    private List<String> fields;

    private String search;
    private String status;
    private String jobId;
    private String company;
    private String batch;
    private String branch;
    private Integer round;
    private LocalDate appliedFrom;
    private LocalDate appliedTo;
}
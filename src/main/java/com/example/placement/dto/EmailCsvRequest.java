package com.example.placement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class EmailCsvRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Must be a valid email address")
    private String recipientEmail;

    private String scope;

    private List<String> studentIds;

    private List<String> columns;

    private String search;
    private List<String> batch;
    private List<String> branch;
    private List<String> section;
    private String studentStatus;
    private String applicationStatus;
    private String company;
    private String job;
    private Integer round;
    private String appliedFrom;
    private String appliedTo;
}
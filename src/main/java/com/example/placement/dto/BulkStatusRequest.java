package com.example.placement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkStatusRequest {

    @NotEmpty(message = "At least one student ID is required")
    private List<String> studentIds;

    @NotBlank(message = "New status is required")
    private String status;
}

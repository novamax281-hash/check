package com.example.placement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkRequest {

    @Size(min = 1, message = "At least one application ID is required")
    private List<String> applicationIds;

    private String adminRemark;

    @Min(value = 1, message = "Target round must be at least 1")
    private Integer targetRound;
}

package com.example.placement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class EvaluationRequest {

    @Min(value = 0, message = "Score must not be negative")
    @Max(value = 1000, message = "Score seems unreasonably high")
    private Double score;

    private String adminRemark;
}

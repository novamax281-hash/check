package com.example.placement.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoundDetail {

    private Integer round;

    private String name;

    private String status;

    private Double score;

    private String adminRemark;

    private LocalDateTime evaluatedAt;
}

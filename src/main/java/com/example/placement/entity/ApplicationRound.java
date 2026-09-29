package com.example.placement.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationRound {
    private Integer round;
    private RoundStatus status = RoundStatus.PENDING;
    private Double score;
    private String adminRemark;
    private LocalDateTime evaluatedAt;

    public ApplicationRound(Integer round) {
        this.round = round;
        this.status = RoundStatus.PENDING;
    }
}

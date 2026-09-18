package com.example.placement.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ApplicationDetail {

    private String applicationId;

    private String studentId;

    private String placementId;

    private String studentName;

    private String studentEmail;

    private String enrollmentno;

    private String phoneNo;

    private String jobId;

    private String company;

    private String role;

    private String jobType;

    private String salary;

    private String skills;

    private String eligibility;

    private String location;

    private String programFee;

    private String duration;

    private String applicationDeadline;

    private String applicationLink;

    private String jobDescription;

    private String openings;

    private String batch;

    private String branch;

    private String status;

    private Integer currentRound;

    private Integer totalRounds;

    private List<RoundDetail> rounds;

    private LocalDateTime applicationDate;

    private LocalDateTime lastUpdated;

    private String adminRemark;
}

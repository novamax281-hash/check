package com.example.placement.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class ApplicationDetail {

    private String applicationId;

    private String studentId;

    private String placementId;

    private String studentName;

    private String studentEmail;

    private String enrollmentno;

    private String phoneNo;

    private String studentCourse;

    private String selectedCourse;

    private String studentBatch;

    private String studentStatus;

    private String studentRole;

    private String section;

    private String department;

    private String college;

    private Double cgpa;

    private Double tenthPercentage;

    private Double twelfthPercentage;

    private Double graduationPercentage;

    private String dob;

    private String gender;

    private String address;

    private String city;

    private String state;

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

    private String eligibleBatch;

    private String eligibleBranch;

    private String branch;

    private String status;

    private Integer currentRound;

    private Integer totalRounds;

    private List<RoundDetail> rounds;

    private LocalDateTime applicationDate;

    private LocalDateTime lastUpdated;

    private String resumeUrl;

    private String adminRemark;
}

package com.example.placement.dto;

import lombok.Data;

import java.util.List;

@Data
public class StudentDetail {

    private String id;
    private String enrollmentno;
    private String name;
    private String email;
    private String phoneno;
    private String role;
    private String studentStatus;
    private String batch;
    private String branch;
    private String section;
    private String course;
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
    private int applicationCount;
    private String applicationStatus;
    private List<ApplicationDetail> applications;
}

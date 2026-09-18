package com.example.placement.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssignmentRequest {

    private String coordinatorId;

    private String branch;

    private String section;

    private String batch;

    private List<String> studentIds;
}
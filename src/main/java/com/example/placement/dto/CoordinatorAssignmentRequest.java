package com.example.placement.dto;

import lombok.Data;

import java.util.List;

@Data
public class CoordinatorAssignmentRequest {

    private List<String> coordinatorIds;
}

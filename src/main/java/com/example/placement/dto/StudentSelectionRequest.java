package com.example.placement.dto;

import lombok.Data;

import java.util.List;

@Data
public class StudentSelectionRequest {

    private List<String> studentIds;
}

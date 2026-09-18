package com.example.placement.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExportRequest {

    private String scope;

    private List<String> studentIds;

    private List<String> columns;

    private String search;
    private List<String> batch;
    private List<String> branch;
    private List<String> section;
    private String studentStatus;
    private String applicationStatus;
    private String company;
    private String job;
    private Integer round;
    private String appliedFrom;
    private String appliedTo;
}
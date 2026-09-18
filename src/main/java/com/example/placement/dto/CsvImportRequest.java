package com.example.placement.dto;

import lombok.Data;

import java.util.List;

@Data
public class CsvImportRequest {

    private String operation;
    private List<String> rows;
}

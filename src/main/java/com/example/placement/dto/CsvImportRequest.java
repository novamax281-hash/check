package com.example.placement.dto;

import java.util.List;

import lombok.Data;

@Data
public class CsvImportRequest {

    private String operation;
    private List<String> rows;
    private List<CsvImportResult.CsvError> errors;
}

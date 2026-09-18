package com.example.placement.dto;

import lombok.Data;

import java.util.List;

@Data
public class CsvImportResult {

    private int totalRows;
    private int successfulRows;
    private int failedRows;
    private int createdRows;
    private int updatedRows;
    private int skippedRows;
    private List<CsvError> errors;

    @Data
    public static class CsvError {
        private int rowNumber;
        private String identifier;
        private String error;
        private String reason;

        public CsvError(int rowNumber, String identifier, String error, String reason) {
            this.rowNumber = rowNumber;
            this.identifier = identifier;
            this.error = error;
            this.reason = reason;
        }
    }
}

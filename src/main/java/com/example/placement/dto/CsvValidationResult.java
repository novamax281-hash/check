package com.example.placement.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CsvValidationResult {

    private int totalRows;
    private int validRows;
    private int errorRows;
    private List<CsvRowResult> rows = new ArrayList<>();
    private List<String> globalErrors = new ArrayList<>();

    @Data
    public static class CsvRowResult {
        private int rowNumber;
        private String enrollment;
        private String name;
        private String email;
        private String status;
        private boolean valid;
        private String error;

        public CsvRowResult(int rowNumber, String enrollment, String name, String email) {
            this.rowNumber = rowNumber;
            this.enrollment = enrollment;
            this.name = name;
            this.email = email;
            this.valid = true;
        }
    }
}

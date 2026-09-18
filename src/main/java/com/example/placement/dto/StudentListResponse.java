package com.example.placement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentListResponse {

    private long total;
    private int page;
    private int pageSize;
    private int totalPages;
    private List<StudentDetail> students;
    private Map<String, Object> stats;
    private Map<String, Object> filterOptions;
}

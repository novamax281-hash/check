package com.example.placement.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /admin/students/bulk-round}. Previously read as a raw
 * {@code Map}, so a non-string id list threw ClassCastException and a non-numeric
 * round threw NumberFormatException, both surfacing as 500.
 */
public class BulkRoundRequest {

    private List<String> applicationIds;

    private List<String> studentIds;

    @NotNull(message = "Target round is required")
    @Min(value = 1, message = "Target round must be at least 1")
    @Max(value = 100, message = "Target round must be 100 or less")
    private Integer targetRound;

    @Size(max = 500, message = "Remark must be 500 characters or less")
    private String adminRemark;

    public List<String> getApplicationIds() {
        return applicationIds;
    }

    public void setApplicationIds(List<String> applicationIds) {
        this.applicationIds = applicationIds;
    }

    public List<String> getStudentIds() {
        return studentIds;
    }

    public void setStudentIds(List<String> studentIds) {
        this.studentIds = studentIds;
    }

    public Integer getTargetRound() {
        return targetRound;
    }

    public void setTargetRound(Integer targetRound) {
        this.targetRound = targetRound;
    }

    public String getAdminRemark() {
        return adminRemark;
    }

    public void setAdminRemark(String adminRemark) {
        this.adminRemark = adminRemark;
    }
}

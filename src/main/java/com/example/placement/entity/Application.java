package com.example.placement.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "Application")
@CompoundIndex(name = "student_job_unique_idx", def = "{'userId': 1, 'jobId': 1}", unique = true)
public class Application {

    @Id
    private String id;

    @Field("userId")
    @Indexed
    private String studentId;

    @Field("jobId")
    @Indexed
    private String placementId;

    private Integer currentRound = 1;

    private String status = "IN_PROGRESS";

    private LocalDateTime applicationDate;

    private LocalDateTime lastUpdated;

    private String resumeUrl;

    private String adminRemark;

    private List<ApplicationRound> rounds = new ArrayList<>();

    public Application(String studentId, String placementId) {
        this.studentId = studentId;
        this.placementId = placementId;
        this.status = ApplicationStatus.IN_PROGRESS.name();
        this.currentRound = 1;
        this.applicationDate = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
        this.rounds = new ArrayList<>();
        this.rounds.add(new ApplicationRound(1));
    }
}

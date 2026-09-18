package com.example.placement.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

// A coordinator marking a student as eligible to apply for a job.
// This is NOT an Application: the student has not applied yet, only been granted visibility of the job.
@Data
@NoArgsConstructor
@Document(collection = "job_student_selections")
@CompoundIndex(name = "job_student_unique_idx", def = "{'jobId': 1, 'studentId': 1}", unique = true)
public class JobStudentSelection {

    @Id
    private String id;

    private String jobId;

    @Indexed
    private String studentId;

    private String coordinatorId;

    private LocalDateTime selectedDate;

    public JobStudentSelection(String jobId, String studentId, String coordinatorId) {
        this.jobId = jobId;
        this.studentId = studentId;
        this.coordinatorId = coordinatorId;
        this.selectedDate = LocalDateTime.now();
    }
}

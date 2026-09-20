package com.example.placement.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "placement_datas")
public class Placement {

    @Id
    private String id;


    @Indexed(unique = true, sparse = true)
    @Field(write = Field.Write.NON_NULL)
    private String gmail_message_id;

    private String company;

    private String jobRole;

    private String jobType;

    private String stipend;

    private String programFee;

    private String duration;

    private String skills;

    private String eligibility;

    private String applicationDeadline;

    private String applicationLink;

    private String jobDescription;

    private String location;

    private String openings;

    private String batch;

    private String branch;

    private String application_date;

    private String website;

    private String driveType;

    private String mode;

    private String ctc;

    private String salaryLpa;

    private Double minTenth;

    private Double minTwelfth;

    private Double minGraduation;

    private Double minCgpa;

    private String likelyDateOfJoining;

    @Indexed
    private String status = "Open";

    private String createdBy;

    private LocalDateTime createdAt;

    private List<PlacementRound> rounds = new ArrayList<>();

    private List<JobDocument> documents = new ArrayList<>();
}

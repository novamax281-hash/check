package com.example.placement.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rounds")
@CompoundIndex(name = "job_round_idx", def = "{'jobId': 1, 'roundNumber': 1}", unique = true)
public class Round {

    @Id
    private String id;

    private String jobId;

    private Integer roundNumber;

    private String roundName;

    private String description;

    public Round(String jobId, Integer roundNumber, String roundName, String description) {
        this.jobId = jobId;
        this.roundNumber = roundNumber;
        this.roundName = roundName;
        this.description = description;
    }
}

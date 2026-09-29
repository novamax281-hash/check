package com.example.placement.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlacementRound {

    private Integer round;

    private String name;

    private String description;

    private String roundType;

    private String date;

    private String time;

    private String duration;

    public PlacementRound(Integer round, String name, String description) {
        this.round = round;
        this.name = name;
        this.description = description;
        this.roundType = name;
    }

    public PlacementRound(Integer round, String name) {
        this.round = round;
        this.name = name;
        this.roundType = name;
    }
}

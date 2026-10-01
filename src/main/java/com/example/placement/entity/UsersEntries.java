package com.example.placement.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "student_data")
public class UsersEntries {

    @MongoId(FieldType.STRING)
    private String id;

    @Indexed(unique = true)
    private String enrollmentno;

    private String phoneno;

    private String name;

    @Indexed(unique = true)
    private String email;

    @JsonIgnore
    @ToString.Exclude
    private String passwordHash;

    /** Plain-text password accepted on create/import only; never serialized back to clients. */
    @JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;

    private String role;

    private String studentStatus;

    private String batch;

    private String branch;

    private String section;

    private String department;

    private String course;

    private String college;

    private Double cgpa;

    /** Semester-wise SGPA values, index 0 = semester 1. Blank semesters are null. */
    private List<Double> sgpas;

    private Double tenthPercentage;

    private Double twelfthPercentage;

    private Double graduationPercentage;

    private String dob;

    private String gender;

    private String address;

    private String city;

    private String state;

    public UsersEntries(String enrollmentno, String phoneno) {
        this.enrollmentno = enrollmentno;
        this.phoneno = phoneno;
        this.role = "Student";
        this.studentStatus = "Active";
    }
}

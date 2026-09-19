package com.example.placement.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

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

    private String email;

    private String role;

    private String studentStatus;

    private String batch;

    private String branch;

    private String section;

    private String department;

    private String course;

    private String college;

    private Double cgpa;

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

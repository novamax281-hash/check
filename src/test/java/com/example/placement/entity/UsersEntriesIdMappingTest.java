package com.example.placement.entity;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class UsersEntriesIdMappingTest {

    @Test
    void idMustBeMappedAsStringToMatchExistingStringIds() throws Exception {
        Field idField = UsersEntries.class.getDeclaredField("id");
        MongoId mongoId = idField.getAnnotation(MongoId.class);

        assertThat(mongoId)
                .as("@Id must be replaced by @MongoId(FieldType.STRING); otherwise Spring Data "
                        + "converts the String id to ObjectId and every save() of an existing "
                        + "student is treated as an insert (E11000 on enrollmentno)")
                .isNotNull();
        assertThat(mongoId.value()).isEqualTo(FieldType.STRING);
    }
}
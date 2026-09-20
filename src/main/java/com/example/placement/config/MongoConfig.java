package com.example.placement.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import jakarta.annotation.PostConstruct;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    @PostConstruct
    void validateMongoUri() {
        if (mongoUri == null || mongoUri.isBlank()) {
            throw new IllegalStateException(
                    "MONGODB_URI is missing. Set it to the complete mongodb:// or mongodb+srv:// URI from MongoDB Atlas.");
        }
        if (!mongoUri.startsWith("mongodb://") && !mongoUri.startsWith("mongodb+srv://")) {
            throw new IllegalStateException(
                    "MONGODB_URI is invalid. It must start with mongodb:// or mongodb+srv://; copy the URI from MongoDB Atlas.");
        }
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new StringToLocalDateTimeConverter()));
    }

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoTimeouts() {
        return builder -> builder
            .applyToClusterSettings(settings -> settings.serverSelectionTimeout(10, TimeUnit.SECONDS))
            .applyToConnectionPoolSettings(settings -> settings
                .maxSize(20)
                .minSize(5)
                .maxConnectionIdleTime(60, TimeUnit.SECONDS)
                .maxWaitTime(10, TimeUnit.SECONDS))
            .applyToSocketSettings(settings -> settings
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS));
    }

    static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {

        private static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Override
        public LocalDateTime convert(String source) {
            if (source == null || source.isBlank()) {
                return null;
            }
            try {
                return LocalDateTime.parse(source);
            } catch (DateTimeParseException ignored) {

            }
            try {
                return LocalDate.parse(source, LEGACY_DATE).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}

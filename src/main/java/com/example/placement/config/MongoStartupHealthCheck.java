package com.example.placement.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.MongoException;

@Component
public class MongoStartupHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(MongoStartupHealthCheck.class);

    private final MongoTemplate mongoTemplate;

    public MongoStartupHealthCheck(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyMongoConnection() {
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            log.info("MongoDB connection verified");
        } catch (MongoException | org.springframework.dao.DataAccessException exception) {
            log.error("MongoDB unreachable - check network/DNS/Atlas whitelist and MONGODB_URI", exception);
        }
    }

    @EventListener(ApplicationFailedEvent.class)
    public void reportStartupFailure(ApplicationFailedEvent event) {
        Throwable cause = rootCause(event.getException());
        if (cause instanceof MongoException || cause instanceof org.springframework.dao.DataAccessException
                || cause instanceof java.net.UnknownHostException) {
            log.error("MongoDB startup failure - check network/DNS/Atlas whitelist and MONGODB_URI: {}",
                    cause.getMessage());
        }
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
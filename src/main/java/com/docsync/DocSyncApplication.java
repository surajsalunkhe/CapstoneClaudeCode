package com.docsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the DocSync Login API Spring Boot application.
 * Enables component scanning under {@code com.docsync} and activates
 * the scheduled cleanup task for expired refresh tokens.
 */
@SpringBootApplication
@EnableScheduling
public class DocSyncApplication {

    /**
     * Launches the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DocSyncApplication.class, args);
    }
}

package com.booknest.bookservice.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configuration for initializing the MySQL FULLTEXT index on the books table.
 * Enables high-performance text searching across titles, authors, and descriptions.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile("!test")
public class FullTextIndexer {

    private final JdbcTemplate jdbcTemplate;

    // Creates the FULLTEXT index on the books table during application startup
    @PostConstruct
    public void initFullTextIndex() {
        try {
            // Safe execution: If the index already exists, MySQL throws an exception, which we safely catch and ignore.
            log.info("Attempting to initialize FULLTEXT index on books...");
            jdbcTemplate.execute("ALTER TABLE books ADD FULLTEXT idx_book_search (title, author, genre, description)");
            log.info("FULLTEXT index successfully created.");
        } catch (Exception e) {
            log.info("FULLTEXT index already exists or could not be created currently. Reason: {}", e.getMessage());
        }
    }
}

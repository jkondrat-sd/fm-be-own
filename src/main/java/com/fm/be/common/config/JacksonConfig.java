package com.fm.be.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.TimeZone;

/**
 * Jackson ObjectMapper configuration for consistent JSON serialization.
 *
 * <ul>
 *   <li>ISO 8601 date format with timezone (not timestamps)</li>
 *   <li>camelCase property naming (default Java convention)</li>
 *   <li>Ignore unknown properties on deserialization</li>
 *   <li>UTC timezone for date/time operations</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    /**
     * Configures the primary ObjectMapper bean used across the application.
     *
     * @return configured ObjectMapper
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register Java 8 date/time (Instant, LocalDate, etc.)
        mapper.registerModule(new JavaTimeModule());

        // ISO 8601 dates, not timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Don't fail on empty beans
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        // Ignore unknown properties during deserialization
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // UTC timezone for all date operations
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        return mapper;
    }
}

package com.fm.be.common.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the enhanced {@link HealthController}.
 * Tests healthy and unhealthy scenarios for both DB and Redis.
 */
@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private HealthController healthController;

    @Test
    @DisplayName("should return UP when both DB and Redis are healthy")
    @SuppressWarnings("unchecked")
    void getHealth_allHealthy_returnsUp() throws Exception {
        // Mock DB healthy
        Connection mockConn = mock(Connection.class);
        when(mockConn.isValid(3)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(mockConn);

        // Mock Redis healthy
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection redisConn = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(redisConn);
        when(redisConn.ping()).thenReturn("PONG");

        Map<String, Object> result = healthController.getHealth();

        assertThat(result.get("status")).isEqualTo("UP");
        assertThat(result.get("uptime")).isNotNull();
        assertThat(result.get("version")).isEqualTo("1.0.0-SNAPSHOT");

        Map<String, Object> services = (Map<String, Object>) result.get("services");
        Map<String, String> dbStatus = (Map<String, String>) services.get("database");
        Map<String, String> redisStatus = (Map<String, String>) services.get("redis");
        assertThat(dbStatus.get("status")).isEqualTo("UP");
        assertThat(redisStatus.get("status")).isEqualTo("UP");
    }

    @Test
    @DisplayName("should return DEGRADED when DB is down")
    @SuppressWarnings("unchecked")
    void getHealth_dbDown_returnsDegraded() throws Exception {
        // Mock DB failure
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        // Mock Redis healthy
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection redisConn = mock(RedisConnection.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(redisConn);
        when(redisConn.ping()).thenReturn("PONG");

        Map<String, Object> result = healthController.getHealth();

        assertThat(result.get("status")).isEqualTo("DEGRADED");

        Map<String, Object> services = (Map<String, Object>) result.get("services");
        Map<String, String> dbStatus = (Map<String, String>) services.get("database");
        assertThat(dbStatus.get("status")).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("should return DEGRADED when Redis is down")
    @SuppressWarnings("unchecked")
    void getHealth_redisDown_returnsDegraded() throws Exception {
        // Mock DB healthy
        Connection mockConn = mock(Connection.class);
        when(mockConn.isValid(3)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(mockConn);

        // Mock Redis failure
        when(redisTemplate.getConnectionFactory()).thenThrow(
                new RuntimeException("Cannot connect to Redis"));

        Map<String, Object> result = healthController.getHealth();

        assertThat(result.get("status")).isEqualTo("DEGRADED");

        Map<String, Object> services = (Map<String, Object>) result.get("services");
        Map<String, String> redisStatus = (Map<String, String>) services.get("redis");
        assertThat(redisStatus.get("status")).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("should return DEGRADED when both services are down")
    @SuppressWarnings("unchecked")
    void getHealth_allDown_returnsDegraded() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("DB down"));
        when(redisTemplate.getConnectionFactory()).thenThrow(
                new RuntimeException("Redis down"));

        Map<String, Object> result = healthController.getHealth();

        assertThat(result.get("status")).isEqualTo("DEGRADED");
    }
}

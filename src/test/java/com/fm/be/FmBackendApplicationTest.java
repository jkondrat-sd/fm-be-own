package com.fm.be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test to verify the Spring application context loads successfully
 * with all dependencies and configurations properly wired.
 */
@SpringBootTest
@ActiveProfiles("test")
class FmBackendApplicationTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring context starts without errors.
        // This catches wiring issues, missing beans, and configuration problems.
    }
}

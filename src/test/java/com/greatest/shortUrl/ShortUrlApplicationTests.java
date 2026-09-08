package com.greatest.shortUrl;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application context load test.
 *
 * This test is disabled because it requires live infrastructure:
 *   - PostgreSQL (DB_URL, DB_USER, DB_PASSWORD env vars)
 *   - Redis / Upstash (REDIS_HOST, REDIS_PORT, REDIS_PASSWORD)
 *
 * To run manually with real infra:
 *   mvn test -Dtest=ShortUrlApplicationTests -Dspring.profiles.active=dev
 *
 * All business logic is covered by the unit tests in:
 *   - services/ShortUrlServiceTest   (16 tests)
 *   - services/AuthServiceTest       (6 tests)
 *   - services/AdminServiceImplTest  (5 tests)
 *   - controller/AuthControllerTest  (6 tests)
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Requires live Postgres + Redis. Run manually with -Dspring.profiles.active=dev")
class ShortUrlApplicationTests {

    @Test
    void contextLoads() {
    }

}

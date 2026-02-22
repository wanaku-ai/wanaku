package ai.wanaku.core.capabilities.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServicesHelperTest {

    @Test
    void waitAndRetryDecrementsRetries() {
        int result = ServicesHelper.waitAndRetry("test-service", new RuntimeException("fail"), 3, 0);
        assertEquals(2, result);
    }

    @Test
    void waitAndRetryReturnsZeroWhenNoRetriesLeft() {
        int result = ServicesHelper.waitAndRetry("test-service", new RuntimeException("fail"), 1, 0);
        assertEquals(0, result);
    }

    @Test
    void waitAndRetryWithZeroRetriesReturnsNegative() {
        // When called with 0 retries, it decrements to -1 and does not hit the == 0
        // check
        int result = ServicesHelper.waitAndRetry("test-service", new RuntimeException("fail"), 0, 0);
        assertEquals(-1, result);
    }

    @Test
    void waitAndRetryWithNegativeRetriesReturnsDecrementedValue() {
        int result = ServicesHelper.waitAndRetry("test-service", new RuntimeException("fail"), -1, 0);
        assertEquals(-2, result);
    }

    @Test
    void getCanonicalServiceHomeExpandsPlaceholder() {
        String userHome = System.getProperty("user.home");
        // Create a minimal config implementation for testing
        TestServiceConfig config = new TestServiceConfig("my-service", "${user.home}/.wanaku");
        String result = ServicesHelper.getCanonicalServiceHome(config);
        assertEquals(java.nio.file.Path.of(userHome, ".wanaku", "my-service").toString(), result);
    }

    @Test
    void getCanonicalServiceHomeWithoutPlaceholder() {
        TestServiceConfig config = new TestServiceConfig("my-service", "/opt/wanaku");
        String result = ServicesHelper.getCanonicalServiceHome(config);
        assertEquals(java.nio.file.Path.of("/opt/wanaku", "my-service").toString(), result);
    }
}

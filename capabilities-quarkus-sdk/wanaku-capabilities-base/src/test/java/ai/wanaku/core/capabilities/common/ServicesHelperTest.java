package ai.wanaku.core.capabilities.common;

import java.io.File;
import java.nio.file.Path;
import io.quarkus.test.junit.QuarkusTest;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ServicesHelperTest {
    private static final String USE_SEPARATE_SERVER = "quarkus.grpc.server.use-separate-server";
    private static final String GRPC_PORT = "quarkus.grpc.server.port";
    private static final String HTTP_HOST_ENABLED = "quarkus.http.host-enabled";
    private static final String HTTP_PORT = "quarkus.http.port";

    @AfterEach
    void clearConfigOverrides() {
        System.clearProperty(USE_SEPARATE_SERVER);
        System.clearProperty(GRPC_PORT);
        System.clearProperty(HTTP_HOST_ENABLED);
        System.clearProperty(HTTP_PORT);
    }

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
    void getCanonicalServiceHome_handlesTrailingSeparatorInServiceHome() {
        String userHome = System.getProperty("user.home");
        TestServiceConfig config =
                new TestServiceConfig("my-service", userHome + File.separator + ".wanaku" + File.separator);

        String result = ServicesHelper.getCanonicalServiceHome(config);

        assertEquals(java.nio.file.Path.of(userHome, ".wanaku", "my-service").toString(), result);
    }

    @Test
    void getCanonicalServiceHomeWithoutPlaceholder() {
        TestServiceConfig config = new TestServiceConfig("my-service", "/opt/wanaku");
        String result = ServicesHelper.getCanonicalServiceHome(config);
        assertEquals(Path.of("/opt/wanaku", "my-service").toString(), result);
    }

    @Test
    void resolveRegistrationPortUsesGrpcPortWhenSeparateServerIsEnabled() {
        System.setProperty(USE_SEPARATE_SERVER, "true");
        System.setProperty(GRPC_PORT, "9123");

        assertEquals(9123, ServicesHelper.resolveRegistrationPort());
    }

    @Test
    void resolveRegistrationPortUsesHttpPortWhenGrpcServerSharesTheHttpListener() {
        System.setProperty(USE_SEPARATE_SERVER, "false");
        System.setProperty(HTTP_HOST_ENABLED, "true");
        System.setProperty(HTTP_PORT, "8085");

        assertEquals(8085, ServicesHelper.resolveRegistrationPort());
    }

    @Test
    void resolveRegistrationPortFailsFastWhenSharedGrpcNeedsAnHttpServer() {
        System.setProperty(USE_SEPARATE_SERVER, "false");
        System.setProperty(HTTP_HOST_ENABLED, "false");

        WanakuException exception = assertThrows(WanakuException.class, ServicesHelper::resolveRegistrationPort);

        assertEquals(
                "quarkus.grpc.server.use-separate-server=false requires quarkus.http.host-enabled=true "
                        + "because the gRPC server shares the HTTP listener in that mode",
                exception.getMessage());
    }
}

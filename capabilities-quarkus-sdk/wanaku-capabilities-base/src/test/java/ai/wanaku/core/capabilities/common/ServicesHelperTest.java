package ai.wanaku.core.capabilities.common;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServicesHelperTest {
    private static final ConfigProviderResolver CONFIG_PROVIDER_RESOLVER = ConfigProviderResolver.instance();
    private ClassLoader originalContextClassLoader;
    private URLClassLoader testContextClassLoader;
    private Config registeredConfig;

    @AfterEach
    void clearConfigOverrides() {
        if (registeredConfig != null) {
            CONFIG_PROVIDER_RESOLVER.releaseConfig(registeredConfig);
            registeredConfig = null;
        }
        if (originalContextClassLoader != null) {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            originalContextClassLoader = null;
        }
        if (testContextClassLoader != null) {
            try {
                testContextClassLoader.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            testContextClassLoader = null;
        }
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
        useConfig(Map.of(
                "quarkus.grpc.server.use-separate-server", "true",
                "quarkus.grpc.server.port", "9123"));

        assertEquals(9123, ServicesHelper.resolveRegistrationPort());
    }

    @Test
    void resolveRegistrationPortUsesHttpPortWhenGrpcServerSharesTheHttpListener() {
        useConfig(Map.of(
                "quarkus.grpc.server.use-separate-server", "false",
                "quarkus.http.host-enabled", "true",
                "quarkus.http.port", "8085"));

        assertEquals(8085, ServicesHelper.resolveRegistrationPort());
    }

    @Test
    void resolveRegistrationPortFailsFastWhenSharedGrpcNeedsAnHttpServer() {
        useConfig(Map.of(
                "quarkus.grpc.server.use-separate-server", "false",
                "quarkus.http.host-enabled", "false"));

        RuntimeException exception = assertThrows(RuntimeException.class, ServicesHelper::resolveRegistrationPort);

        assertEquals(
                "quarkus.grpc.server.use-separate-server=false requires quarkus.http.host-enabled=true "
                        + "because the gRPC server shares the HTTP listener in that mode",
                exception.getMessage());
    }

    private void useConfig(Map<String, String> properties) {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        testContextClassLoader = new URLClassLoader(new URL[0], originalContextClassLoader);
        registeredConfig = CONFIG_PROVIDER_RESOLVER
                .getBuilder()
                .withSources(new org.eclipse.microprofile.config.spi.ConfigSource() {
                    @Override
                    public Map<String, String> getProperties() {
                        return properties;
                    }

                    @Override
                    public java.util.Set<String> getPropertyNames() {
                        return properties.keySet();
                    }

                    @Override
                    public String getValue(String propertyName) {
                        return properties.get(propertyName);
                    }

                    @Override
                    public String getName() {
                        return "services-helper-test";
                    }
                })
                .build();

        CONFIG_PROVIDER_RESOLVER.registerConfig(registeredConfig, testContextClassLoader);
        Thread.currentThread().setContextClassLoader(testContextClassLoader);
    }
}

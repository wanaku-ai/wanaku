package ai.wanaku.core.persistence.file;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.api.types.management.State;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ServiceRegistryTest {

    @Inject
    ServiceRegistry serviceRegistry;

    private static Path basePath;
    private static Path servicePath;
    private static final String TEST_SERVICE_NAME = "myService";
    private static Path statusPath;

    @BeforeAll
    public static void init() throws IOException {
        basePath = Path.of(
                ConfigProvider.getConfig().getValue("wanaku.persistence.file.base-folder", String.class));
        servicePath = basePath.resolve(ConfigProvider.getConfig().getValue("wanaku.persistence.file.services", String.class));

        if (Files.exists(servicePath)) {
            Files.write(servicePath, "".getBytes());
        }

        statusPath = basePath.resolve("state-" + TEST_SERVICE_NAME + ".json");

        if (Files.exists(statusPath)) {
            Files.write(statusPath, "".getBytes());
        }
    }

    @Test
    @Order(1)
    public void register() throws IOException {
        ServiceTarget serviceTarget = new ServiceTarget(TEST_SERVICE_NAME, "localhost", 8081, ServiceType.TOOL_INVOKER);

        serviceRegistry.register(serviceTarget, Map.of("myProperty", "myDescription"));

        Assertions.assertTrue(Files.readString(servicePath).contains("myProperty"));
    }

    @Test
    @Order(2)
    public void getService() {
        Service service = serviceRegistry.getService(TEST_SERVICE_NAME);

        Assertions.assertEquals("localhost:8081", service.getTarget());
    }

    @Test
    @Order(3)
    public void getServiceByType() {
        Map<String, Service> tools = serviceRegistry.getEntries(ServiceType.TOOL_INVOKER);
        Map<String, Service> resources = serviceRegistry.getEntries(ServiceType.RESOURCE_PROVIDER);

        Assertions.assertEquals(1, tools.size());
        Assertions.assertEquals(0, resources.size());
    }

    @Test
    @Order(4)
    public void saveState() throws IOException {
        serviceRegistry.saveState(TEST_SERVICE_NAME, true, "myMessage");

        Assertions.assertTrue(Files.readString(statusPath).contains("myMessage"));
    }

    @Test
    @Order(5)
    public void getState() {
        List<State> states = serviceRegistry.getState(TEST_SERVICE_NAME, 10);

        Assertions.assertEquals(1, states.size());
    }

    @Test
    @Order(6)
    public void updateProperty() throws IOException {
        Assertions.assertFalse(Files.readString(servicePath).contains("myValue"));

        serviceRegistry.update(TEST_SERVICE_NAME, "myProperty", "myValue");

        Assertions.assertTrue(Files.readString(servicePath).contains("myValue"));

        Service service = serviceRegistry.getService(TEST_SERVICE_NAME);

        Assertions.assertEquals("myValue", service.getConfigurations().getConfigurations().get("myProperty").getValue());
    }

    @Test
    @Order(7)
    public void deregister() {
        // The testServiceName is a TOOL, this line of code should not deregister
        serviceRegistry.deregister(TEST_SERVICE_NAME, ServiceType.RESOURCE_PROVIDER);

        Assertions.assertNotNull(serviceRegistry.getService(TEST_SERVICE_NAME));

        serviceRegistry.deregister(TEST_SERVICE_NAME, ServiceType.TOOL_INVOKER);

        // De-registering a service with the file-based one, should not remove the entry (see issue #253).
        Assertions.assertDoesNotThrow(() -> serviceRegistry.getService(TEST_SERVICE_NAME));
    }

    @Test
    @Order(100)
    public void testConcurrentRegistrations() throws InterruptedException {
        List<Callable<Void>> callables = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            int finalI = i;
            callables.add(() -> {
                ServiceTarget serviceTarget = new ServiceTarget(TEST_SERVICE_NAME + finalI, "localhost", 8081, ServiceType.TOOL_INVOKER);
                serviceRegistry.register(serviceTarget, Map.of("myProperty", "myDescription"));
                return null;
            });
        }

        Executors.newFixedThreadPool(10)
                .invokeAll(callables).forEach(f -> {
                            try {
                                f.get();
                            } catch (Exception e) {
                                Assertions.fail(e);
                            }
                        }
                );
    }
}

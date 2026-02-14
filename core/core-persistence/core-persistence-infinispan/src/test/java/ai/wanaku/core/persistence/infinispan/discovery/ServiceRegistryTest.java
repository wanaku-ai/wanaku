package ai.wanaku.core.persistence.infinispan.discovery;

import jakarta.inject.Inject;

import java.util.List;
import io.quarkus.test.junit.QuarkusTest;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.discovery.ServiceState;
import ai.wanaku.capabilities.sdk.api.types.discovery.StandardMessages;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.core.mcp.providers.ServiceRegistry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ServiceRegistryTest {
    private static final String SERVICE_TYPE_TOOL_INVOKER = ServiceType.TOOL_INVOKER.asValue();
    private static final String SERVICE_TYPE_RESOURCE_PROVIDER = ServiceType.RESOURCE_PROVIDER.asValue();

    @Inject
    ServiceRegistry serviceRegistry;

    private static final String TEST_SERVICE_ID = "service1";
    private static final String TEST_SERVICE_NAME = "myService";

    @BeforeAll
    public void setup() {
        ((InfinispanServiceRegistry) serviceRegistry).clear();
    }

    @Test
    @Order(1)
    public void register() {
        ServiceTarget serviceTarget = new ServiceTarget(
                TEST_SERVICE_ID,
                TEST_SERVICE_NAME,
                "localhost",
                8081,
                SERVICE_TYPE_TOOL_INVOKER,
                "mcp",
                null,
                null,
                null);

        Assertions.assertDoesNotThrow(() -> serviceRegistry.register(serviceTarget));
    }

    @Test
    @Order(2)
    public void getServiceByName() {
        List<ServiceTarget> services = serviceRegistry.getServiceByName(TEST_SERVICE_NAME, SERVICE_TYPE_TOOL_INVOKER);
        assertFalse(services.isEmpty());
        ServiceTarget service = services.getFirst();
        assertEquals("localhost", service.getHost());
        assertEquals(8081, service.getPort());
    }

    @Test
    @Order(3)
    public void getServiceByNameByType() {
        List<ServiceTarget> tools = serviceRegistry.getEntries(SERVICE_TYPE_TOOL_INVOKER);
        List<ServiceTarget> resources = serviceRegistry.getEntries(SERVICE_TYPE_RESOURCE_PROVIDER);

        assertEquals(1, tools.size());
        assertEquals(0, resources.size());
        assertEquals(TEST_SERVICE_NAME, tools.getFirst().getServiceName());
    }

    @Test
    @Order(4)
    public void updateProperty() {
        ServiceTarget serviceTarget = new ServiceTarget(
                TEST_SERVICE_ID,
                TEST_SERVICE_NAME,
                "localhost",
                8081,
                SERVICE_TYPE_TOOL_INVOKER,
                "mcp",
                null,
                null,
                null);

        serviceRegistry.update(serviceTarget);

        List<ServiceTarget> tools = serviceRegistry.getEntries(SERVICE_TYPE_TOOL_INVOKER);

        assertEquals(1, tools.size());
        assertEquals(TEST_SERVICE_NAME, tools.getFirst().getServiceName());

        final ServiceTarget service = tools.getFirst();
    }

    @Test
    @Order(5)
    public void deregister() {
        ServiceTarget serviceTarget = new ServiceTarget(
                TEST_SERVICE_ID, TEST_SERVICE_NAME, "localhost", 0, SERVICE_TYPE_TOOL_INVOKER, "mcp", null, null, null);

        serviceRegistry.deregister(serviceTarget);

        List<ServiceTarget> tools = serviceRegistry.getEntries(SERVICE_TYPE_TOOL_INVOKER);
        assertEquals(0, tools.size());
    }

    @Test
    @Order(6)
    void testUpdateStateHealthy() {
        Assertions.assertDoesNotThrow(
                () -> serviceRegistry.updateLastState(TEST_SERVICE_ID, ServiceState.newHealthy()));
    }

    @Test
    @Order(7)
    void testUpdateStateUnhealthy() {
        Assertions.assertDoesNotThrow(
                () -> serviceRegistry.updateLastState(TEST_SERVICE_ID, ServiceState.newUnhealthy("test")));
    }

    @Test
    @Order(8)
    public void getState() {
        ActivityRecord record = serviceRegistry.getStates(TEST_SERVICE_ID);

        Assertions.assertNotNull(record);
        final List<ServiceState> states = record.getStates();
        Assertions.assertNotNull(states);
        assertEquals(2, record.getStates().size());
        final ServiceState first = record.getStates().getFirst();
        assertEquals(StandardMessages.HEALTHY, first.getReason());
        assertTrue(first.isHealthy());

        final ServiceState second = record.getStates().getLast();
        assertEquals("test", second.getReason());
        assertFalse(second.isHealthy());
    }
}

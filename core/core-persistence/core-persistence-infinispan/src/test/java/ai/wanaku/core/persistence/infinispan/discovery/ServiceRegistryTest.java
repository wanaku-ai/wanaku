package ai.wanaku.core.persistence.infinispan.discovery;

import jakarta.inject.Inject;

import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.discovery.ServiceState;
import ai.wanaku.api.types.discovery.StandardMessages;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
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
        ServiceTarget serviceTarget = new ServiceTarget(TEST_SERVICE_ID, TEST_SERVICE_NAME, "localhost", 8081, ServiceType.TOOL_INVOKER, Map.of("myProperty", "myDescription"));

        Assertions.assertDoesNotThrow(() -> serviceRegistry.register(serviceTarget));
    }

    @Test
    @Order(2)
    public void getServiceByName() {
        ServiceTarget service = serviceRegistry.getServiceByName(TEST_SERVICE_NAME, ServiceType.TOOL_INVOKER);

        assertEquals("localhost", service.getHost());
        assertEquals(8081, service.getPort());
    }

    @Test
    @Order(3)
    public void getServiceByNameByType() {
        List<ServiceTarget> tools = serviceRegistry.getEntries(ServiceType.TOOL_INVOKER);
        List<ServiceTarget> resources = serviceRegistry.getEntries(ServiceType.RESOURCE_PROVIDER);

        assertEquals(1, tools.size());
        assertEquals(0, resources.size());
        assertEquals(TEST_SERVICE_NAME, tools.getFirst().getService());
    }

    @Test
    @Order(4)
    public void updateProperty() {
        ServiceTarget serviceTarget = new ServiceTarget(TEST_SERVICE_ID,  TEST_SERVICE_NAME, "localhost", 8081, ServiceType.TOOL_INVOKER, Map.of("myProperty", "myDescription-2"));

        serviceRegistry.update(serviceTarget);

        List<ServiceTarget> tools = serviceRegistry.getEntries(ServiceType.TOOL_INVOKER);

        assertEquals(1, tools.size());
        assertEquals(TEST_SERVICE_NAME, tools.getFirst().getService());

        final ServiceTarget service = tools.getFirst();

        assertEquals("myDescription-2", service.getConfigurations().get("myProperty"));
    }

    @Test
    @Order(5)
    public void deregister() {
        ServiceTarget serviceTarget = new ServiceTarget(TEST_SERVICE_ID, TEST_SERVICE_NAME, "localhost", 0, ServiceType.TOOL_INVOKER, Map.of());

        serviceRegistry.deregister(serviceTarget);

        List<ServiceTarget> tools = serviceRegistry.getEntries(ServiceType.TOOL_INVOKER);
        assertEquals(0, tools.size());
    }

    @Test
    @Order(6)
    void testUpdateStateHealthy() {
        Assertions.assertDoesNotThrow(() -> serviceRegistry.updateLastState(TEST_SERVICE_ID, ServiceState.newHealthy()));
    }

    @Test
    @Order(7)
    void testUpdateStateUnhealthy() {
        Assertions.assertDoesNotThrow(() -> serviceRegistry.updateLastState(TEST_SERVICE_ID, ServiceState.newUnhealthy("test")));
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

package ai.wanaku.backend.service.support;

import java.util.Collections;
import java.util.List;
import ai.wanaku.backend.core.mcp.providers.ServiceRegistry;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class FirstAvailableTest {
    private static final String SERVICE_TYPE_TOOL_INVOKER = ServiceType.TOOL_INVOKER.asValue();

    FirstAvailable firstAvailable;

    ServiceRegistry mockRegistry;

    @BeforeEach
    public void setup() {
        mockRegistry = Mockito.mock(ServiceRegistry.class);
        firstAvailable = new FirstAvailable(mockRegistry);
    }

    @Test
    public void testResolveService() {
        ServiceTarget service1 = new ServiceTarget(
                "id1", "service-a", "localhost", 8080, SERVICE_TYPE_TOOL_INVOKER, "mcp", null, null, null);
        ServiceTarget service2 = new ServiceTarget(
                "id2", "service-a", "localhost", 8081, SERVICE_TYPE_TOOL_INVOKER, "mcp", null, null, null);
        List<ServiceTarget> targets = List.of(service1, service2);

        Mockito.when(mockRegistry.getServiceByName("service-a", SERVICE_TYPE_TOOL_INVOKER))
                .thenReturn(targets);

        ServiceTarget resolved = firstAvailable.resolve("service-a", SERVICE_TYPE_TOOL_INVOKER);
        Assertions.assertNotNull(resolved);
        Assertions.assertEquals("id1", resolved.getId());
    }

    @Test
    public void testResolveServiceNotFound() {
        Mockito.when(mockRegistry.getServiceByName("service-b", SERVICE_TYPE_TOOL_INVOKER))
                .thenReturn(Collections.emptyList());

        ServiceTarget resolved = firstAvailable.resolve("service-b", SERVICE_TYPE_TOOL_INVOKER);
        Assertions.assertNull(resolved);
    }

    @Test
    public void testResolveServiceNull() {
        Mockito.when(mockRegistry.getServiceByName("service-c", SERVICE_TYPE_TOOL_INVOKER))
                .thenReturn(null);

        ServiceTarget resolved = firstAvailable.resolve("service-c", SERVICE_TYPE_TOOL_INVOKER);
        Assertions.assertNull(resolved);
    }
}

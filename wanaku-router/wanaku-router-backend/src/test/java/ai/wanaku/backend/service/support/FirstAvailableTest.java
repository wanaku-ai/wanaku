package ai.wanaku.backend.service.support;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.backend.support.WanakuKeycloakTestResource;
import ai.wanaku.backend.support.WanakuRouterTest;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.mockito.Mockito;

@QuarkusTest
@QuarkusTestResource(value = WanakuKeycloakTestResource.class, restrictToAnnotatedClass = true)
@DisabledIf(value = "isUnsupportedOSOnGithub", disabledReason = "Does not run on macOS or Windows on GitHub")
public class FirstAvailableTest extends WanakuRouterTest {

    FirstAvailable firstAvailable;

    ServiceRegistry mockRegistry;

    @BeforeEach
    public void setup() {
        mockRegistry = Mockito.mock(ServiceRegistry.class);
        firstAvailable = new FirstAvailable(mockRegistry);
    }

    @Test
    public void testResolveService() {
        ServiceTarget service1 = new ServiceTarget("id1", "service-a", "localhost", 8080, ServiceType.TOOL_INVOKER);
        ServiceTarget service2 = new ServiceTarget("id2", "service-a", "localhost", 8081, ServiceType.TOOL_INVOKER);
        List<ServiceTarget> targets = List.of(service1, service2);

        Mockito.when(mockRegistry.getServiceByName("service-a", ServiceType.TOOL_INVOKER))
                .thenReturn(targets);

        ServiceTarget resolved = firstAvailable.resolve("service-a", ServiceType.TOOL_INVOKER);
        Assertions.assertNotNull(resolved);
        Assertions.assertEquals("id1", resolved.getId());
    }

    @Test
    public void testResolveServiceNotFound() {
        Mockito.when(mockRegistry.getServiceByName("service-b", ServiceType.TOOL_INVOKER))
                .thenReturn(Collections.emptyList());

        ServiceTarget resolved = firstAvailable.resolve("service-b", ServiceType.TOOL_INVOKER);
        Assertions.assertNull(resolved);
    }

    @Test
    public void testResolveServiceNull() {
        Mockito.when(mockRegistry.getServiceByName("service-c", ServiceType.TOOL_INVOKER))
                .thenReturn(null);

        ServiceTarget resolved = firstAvailable.resolve("service-c", ServiceType.TOOL_INVOKER);
        Assertions.assertNull(resolved);
    }
}

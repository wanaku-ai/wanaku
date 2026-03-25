package ai.wanaku.backend.api.v1.namespaces;

import jakarta.inject.Inject;

import java.util.List;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import ai.wanaku.backend.api.v1.resources.ResourcesBean;
import ai.wanaku.backend.api.v1.tools.ToolsBean;
import ai.wanaku.backend.support.NoOidcTestProfile;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(NoOidcTestProfile.class)
public class NamespacesBeanTest {

    @Inject
    NamespacesBean namespacesBean;

    @Inject
    ToolsBean toolsBean;

    @Inject
    ResourcesBean resourcesBean;

    @Test
    void testListNamespaces_EmptyHasDefaultNamespace() {
        List<Namespace> namespaces = namespacesBean.list();
        assertThat(namespaces).extracting(Namespace::getName).contains("default");
        long count =
                namespaces.stream().filter(ns -> "default".equals(ns.getName())).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testListNamespaces_FromTools() {
        // Add tools with different namespaces
        ToolReference tool1 = new ToolReference();
        tool1.setName("tool1");
        tool1.setNamespace("namespace-a");
        tool1.setType("http");
        tool1.setUri("https://example.com");
        tool1.setDescription("Description 1");

        ToolReference tool2 = new ToolReference();
        tool2.setName("tool2");
        tool2.setNamespace("namespace-b");
        tool2.setType("http");
        tool2.setUri("https://example.com");
        tool2.setDescription("Description 2");

        toolsBean.add(tool1);
        toolsBean.add(tool2);

        // List namespaces
        List<Namespace> namespaces = namespacesBean.list();

        assertThat(namespaces).extracting(Namespace::getName).contains("namespace-a", "namespace-b");
    }

    @Test
    void testListNamespaces_FromResources() {
        // Add resources with namespaces
        ResourceReference resource1 = new ResourceReference();
        resource1.setName("resource1");
        resource1.setNamespace("res-namespace-a");
        resource1.setLocation("file:///tmp/a");
        resource1.setDescription("Resource Description 1");

        ResourceReference resource2 = new ResourceReference();
        resource2.setName("resource2");
        resource2.setNamespace("res-namespace-b");
        resource2.setLocation("file:///tmp/b");
        resource2.setDescription("Resource Description 2");

        resourcesBean.expose(resource1);
        resourcesBean.expose(resource2);

        // List namespaces
        List<Namespace> namespaces = namespacesBean.list();

        assertThat(namespaces).extracting(Namespace::getName).contains("res-namespace-a", "res-namespace-b");
    }

    @Test
    void testListNamespaces_MixedToolsAndResources() {
        // Add tool
        ToolReference tool = new ToolReference();
        tool.setName("tool");
        tool.setNamespace("mixed-namespace");
        tool.setType("http");
        tool.setUri("https://example.com");
        tool.setDescription("Mixed Description");

        toolsBean.add(tool);

        // Add resource with different namespace
        ResourceReference resource = new ResourceReference();
        resource.setName("resource");
        resource.setNamespace("another-namespace");
        resource.setLocation("file:///tmp/test");
        resource.setDescription("Another Description");

        resourcesBean.expose(resource);

        // List namespaces
        List<Namespace> namespaces = namespacesBean.list();

        assertThat(namespaces).extracting(Namespace::getName).contains("mixed-namespace", "another-namespace");
    }

    @Test
    void testListNamespaces_Uniqueness() {
        // Add multiple tools with same namespace
        for (int i = 0; i < 5; i++) {
            ToolReference tool = new ToolReference();
            tool.setName("tool-" + i);
            tool.setNamespace("shared-namespace");
            tool.setType("http");
            tool.setUri("https://example.com");
            tool.setDescription("Shared Description " + i);
            toolsBean.add(tool);
        }
        // List namespaces
        List<Namespace> namespaces = namespacesBean.list();
        // Verify namespace appears only once
        long count = namespaces.stream()
                .filter(ns -> "shared-namespace".equals(ns.getName()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testListNamespaces_UniquenessAcrossToolsAndResources() {
        String sharedNamespace = "shared-namespace";
        // Add a tool with the shared namespace
        ToolReference tool = new ToolReference();
        tool.setName("tool-shared");
        tool.setNamespace(sharedNamespace);
        tool.setType("http");
        tool.setUri("https://example.com/tool");
        tool.setDescription("Shared namespace tool");
        toolsBean.add(tool);
        // Add a resource with the same namespace
        ResourceReference resource = new ResourceReference();
        resource.setName("resource-shared");
        resource.setNamespace(sharedNamespace);
        resource.setType("http");
        resource.setLocation("https://example.com/resource");
        resource.setDescription("Shared namespace resource");
        resourcesBean.expose(resource);
        // List namespaces
        List<Namespace> namespaces = namespacesBean.list();
        // Verify the shared namespace appears only once across tools and resources
        long count = namespaces.stream()
                .filter(ns -> sharedNamespace.equals(ns.getName()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testListNamespaces_DefaultNamespaceWhenUsed() {
        // The "default" namespace should already exist and remain unique after usage.
        // Add tool with default namespace
        ToolReference tool = new ToolReference();
        tool.setName("default-tool");
        tool.setNamespace("default");
        tool.setType("http");
        tool.setUri("https://example.com");
        tool.setDescription("Default Description");

        toolsBean.add(tool);

        // List namespaces
        List<Namespace> namespaces = namespacesBean.list();

        long count =
                namespaces.stream().filter(ns -> "default".equals(ns.getName())).count();
        assertThat(count).isEqualTo(1);
    }
}

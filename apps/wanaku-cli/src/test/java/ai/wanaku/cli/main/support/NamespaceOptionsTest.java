package ai.wanaku.cli.main.support;

import java.lang.reflect.Field;
import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.NamespacesService;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NamespaceOptionsTest {

    private NamespaceOptions createWithNamespace(String name) throws Exception {
        NamespaceOptions options = new NamespaceOptions();
        Field field = NamespaceOptions.class.getDeclaredField("namespace");
        field.setAccessible(true);
        field.set(options, name);
        return options;
    }

    private NamespaceOptions createWithNamespaceId(String id) throws Exception {
        NamespaceOptions options = new NamespaceOptions();
        Field field = NamespaceOptions.class.getDeclaredField("namespaceId");
        field.setAccessible(true);
        field.set(options, id);
        return options;
    }

    @Test
    void resolveNamespaceNameReturnsNameWhenNamespaceProvided() throws Exception {
        NamespaceOptions options = createWithNamespace("default");
        NamespacesService service = mock(NamespacesService.class);

        String result = options.resolveNamespaceName(service);

        assertEquals("default", result);
    }

    @Test
    void resolveNamespaceNameLooksUpByIdWhenNamespaceIdProvided() throws Exception {
        NamespaceOptions options = createWithNamespaceId("4ff7507f-a88c-47a7-b6e0-4a09e11b0a63");
        NamespacesService service = mock(NamespacesService.class);

        Namespace ns = new Namespace();
        ns.setName("default");
        ns.setPath("default");

        when(service.getById("4ff7507f-a88c-47a7-b6e0-4a09e11b0a63")).thenReturn(new WanakuResponse<>(ns));

        String result = options.resolveNamespaceName(service);

        assertEquals("default", result);
    }

    @Test
    void resolveNamespaceNameThrowsWhenNamespaceIdNotFound() throws Exception {
        NamespaceOptions options = createWithNamespaceId("nonexistent-id");
        NamespacesService service = mock(NamespacesService.class);

        when(service.getById("nonexistent-id")).thenReturn(new WanakuResponse<>((Namespace) null));

        assertThrows(IllegalArgumentException.class, () -> options.resolveNamespaceName(service));
    }

    @Test
    void resolveNamespaceNameThrowsWhenNamespaceHasNoName() throws Exception {
        NamespaceOptions options = createWithNamespaceId("preallocated-id");
        NamespacesService service = mock(NamespacesService.class);

        Namespace ns = new Namespace();
        ns.setName(null);
        ns.setPath("ns-1");

        when(service.getById("preallocated-id")).thenReturn(new WanakuResponse<>(ns));

        assertThrows(IllegalArgumentException.class, () -> options.resolveNamespaceName(service));
    }

    @Test
    void resolveNamespaceNameThrowsWhenNeitherProvided() throws Exception {
        NamespaceOptions options = new NamespaceOptions();
        NamespacesService service = mock(NamespacesService.class);

        assertThrows(IllegalArgumentException.class, () -> options.resolveNamespaceName(service));
    }

    @Test
    void resolveNamespaceIdReturnsIdDirectlyWhenNamespaceIdProvided() throws Exception {
        NamespaceOptions options = createWithNamespaceId("4ff7507f-a88c-47a7-b6e0-4a09e11b0a63");
        NamespacesService service = mock(NamespacesService.class);

        String result = options.resolveNamespaceId(service);

        assertEquals("4ff7507f-a88c-47a7-b6e0-4a09e11b0a63", result);
    }

    @Test
    void resolveNamespaceIdLooksUpByNameWhenNamespaceProvided() throws Exception {
        NamespaceOptions options = createWithNamespace("default");
        NamespacesService service = mock(NamespacesService.class);

        Namespace ns = new Namespace();
        ns.setId("4ff7507f-a88c-47a7-b6e0-4a09e11b0a63");
        ns.setName("default");

        when(service.list()).thenReturn(new WanakuResponse<>(List.of(ns)));

        String result = options.resolveNamespaceId(service);

        assertEquals("4ff7507f-a88c-47a7-b6e0-4a09e11b0a63", result);
    }
}

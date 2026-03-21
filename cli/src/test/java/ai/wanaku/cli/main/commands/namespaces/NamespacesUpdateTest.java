package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.core.Response;

import java.util.Map;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.NamespacesService;
import ai.wanaku.cli.main.support.WanakuPrinter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamespacesUpdateTest {

    @Mock
    private NamespacesService namespacesService;

    private NamespacesUpdate command;

    @BeforeEach
    void setUp() {
        command = new NamespacesUpdate();
        command.namespacesService = namespacesService;
        command.host = "http://localhost:8080";
    }

    @Test
    @DisplayName("Should update namespace fields and merge labels")
    void shouldUpdateNamespaceFieldsAndMergeLabels() throws Exception {
        Namespace existing = new Namespace();
        existing.setId("ns-1");
        existing.setPath("ns-old");
        existing.setName("old");
        existing.setLabels(Map.of("env", "dev"));

        when(namespacesService.getById("ns-1")).thenReturn(new WanakuResponse<>(existing));
        when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(mock(Response.class));

        command.id = "ns-1";
        command.name = "new";
        command.path = "ns-new";
        command.labels = Map.of("tier", "backend");

        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(0, result);
        ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
        verify(namespacesService).update(anyString(), captor.capture());
        Namespace updated = captor.getValue();
        assertEquals("new", updated.getName());
        assertEquals("ns-new", updated.getPath());
        assertEquals("dev", updated.getLabels().get("env"));
        assertEquals("backend", updated.getLabels().get("tier"));
    }

    @Test
    @DisplayName("Should clear name when requested")
    void shouldClearNameWhenRequested() throws Exception {
        Namespace existing = new Namespace();
        existing.setId("ns-1");
        existing.setPath("ns-old");
        existing.setName("old");

        when(namespacesService.getById("ns-1")).thenReturn(new WanakuResponse<>(existing));
        when(namespacesService.update(anyString(), any(Namespace.class))).thenReturn(mock(Response.class));

        command.id = "ns-1";
        command.clearName = true;

        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(0, result);
        ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
        verify(namespacesService).update(anyString(), captor.capture());
        Namespace updated = captor.getValue();
        assertEquals(null, updated.getName());
    }

    @Test
    @DisplayName("Should return error when no updates specified")
    void shouldReturnErrorWhenNoUpdatesSpecified() throws Exception {
        command.id = "ns-1";
        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(1, result);
        verify(namespacesService, never()).getById(anyString());
        verify(namespacesService, never()).update(anyString(), any());
    }
}

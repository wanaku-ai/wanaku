package ai.wanaku.cli.main.commands.namespaces;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamespacesCreateTest {

    @Mock
    private NamespacesService namespacesService;

    private NamespacesCreate command;

    @BeforeEach
    void setUp() {
        command = new NamespacesCreate();
        command.namespacesService = namespacesService;
        command.host = "http://localhost:8080";
    }

    @Test
    @DisplayName("Should create namespace with name and labels")
    void shouldCreateNamespaceWithNameAndLabels() throws Exception {
        Namespace created = new Namespace();
        created.setId("ns-1");
        created.setName("team");
        created.setPath("ns-team");

        when(namespacesService.create(any(Namespace.class))).thenReturn(new WanakuResponse<>(created));

        command.path = "ns-team";
        command.name = "team";
        command.labels = Map.of("env", "dev");

        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(0, result);
        ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
        verify(namespacesService).create(captor.capture());
        Namespace submitted = captor.getValue();
        assertEquals("team", submitted.getName());
        assertEquals("ns-team", submitted.getPath());
        assertEquals("dev", submitted.getLabels().get("env"));
    }

    @Test
    @DisplayName("Should create pre-allocated namespace when name is blank")
    void shouldCreatePreallocatedNamespaceWhenNameBlank() throws Exception {
        Namespace created = new Namespace();
        created.setId("ns-2");
        created.setPath("ns-prealloc");

        when(namespacesService.create(any(Namespace.class))).thenReturn(new WanakuResponse<>(created));

        command.path = "ns-prealloc";
        command.name = "   ";

        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(0, result);
        ArgumentCaptor<Namespace> captor = ArgumentCaptor.forClass(Namespace.class);
        verify(namespacesService).create(captor.capture());
        Namespace submitted = captor.getValue();
        assertEquals(null, submitted.getName());
    }
}

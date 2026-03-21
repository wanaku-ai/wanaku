package ai.wanaku.cli.main.commands.namespaces;

import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.core.services.api.NamespacesService;
import ai.wanaku.cli.main.support.WanakuPrinter;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamespacesShowTest {

    @Mock
    private NamespacesService namespacesService;

    private NamespacesShow command;

    @BeforeEach
    void setUp() {
        command = new NamespacesShow();
        command.namespacesService = namespacesService;
        command.host = "http://localhost:8080";
    }

    @Test
    @DisplayName("Should show namespace when found")
    void shouldShowNamespaceWhenFound() throws Exception {
        Namespace namespace = new Namespace();
        namespace.setId("ns-1");
        namespace.setPath("ns-team");

        when(namespacesService.getById("ns-1")).thenReturn(new WanakuResponse<>(namespace));

        command.id = "ns-1";
        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(0, result);
    }

    @Test
    @DisplayName("Should return error when namespace not found")
    void shouldReturnErrorWhenNamespaceNotFound() throws Exception {
        when(namespacesService.getById("missing")).thenReturn(new WanakuResponse<>(null));

        command.id = "missing";
        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(1, result);
    }
}

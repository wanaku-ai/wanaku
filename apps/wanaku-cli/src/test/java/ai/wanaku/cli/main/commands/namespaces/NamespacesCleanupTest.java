package ai.wanaku.cli.main.commands.namespaces;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamespacesCleanupTest {

    @Mock
    private NamespacesService namespacesService;

    private NamespacesCleanup command;

    @BeforeEach
    void setUp() {
        command = new NamespacesCleanup();
        command.namespacesService = namespacesService;
        command.host = "http://localhost:8080";
    }

    @Test
    @DisplayName("Should skip cleanup when no stale namespaces")
    void shouldSkipCleanupWhenNoStaleNamespaces() throws Exception {
        when(namespacesService.listStale(anyLong(), anyBoolean(), anyBoolean()))
                .thenReturn(new WanakuResponse<>(List.of()));

        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(0, result);
        verify(namespacesService, never()).cleanupStale(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Should cleanup stale namespaces when confirmed")
    void shouldCleanupStaleNamespacesWhenConfirmed() throws Exception {
        Namespace stale = new Namespace();
        stale.setId("ns-1");
        stale.setPath("ns-stale");

        when(namespacesService.listStale(anyLong(), anyBoolean(), anyBoolean()))
                .thenReturn(new WanakuResponse<>(List.of(stale)));
        when(namespacesService.cleanupStale(anyLong(), anyBoolean(), anyBoolean()))
                .thenReturn(new WanakuResponse<>(1));

        command.assumeYes = true;
        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(0, result);
        verify(namespacesService).cleanupStale(anyLong(), anyBoolean(), anyBoolean());
    }
}

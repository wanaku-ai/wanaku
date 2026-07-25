package ai.wanaku.cli.main.commands.namespaces;

import java.util.List;
import java.util.Map;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamespaceListTest {

    @Mock
    private NamespacesService namespacesService;

    private NamespaceList command;

    @BeforeEach
    void setUp() {
        command = new NamespaceList();
        command.namespacesService = namespacesService;
        command.host = "http://localhost:8080";
    }

    @Test
    @DisplayName("Should list namespaces from backend without adding synthetic default")
    void shouldListNamespacesWithoutSyntheticDefault() throws Exception {
        Namespace ns = new Namespace();
        ns.setId("ns-1");
        ns.setPath("ns-team");
        ns.setName("team");

        when(namespacesService.list(null)).thenReturn(new WanakuResponse<>(List.of(ns)));

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(0, result);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(printer).printTable(captor.capture(), eq("id"), eq("name"), eq("path"), eq("labels"));

        List<?> printed = captor.getValue();
        assertEquals(1, printed.size(), "Should contain only the namespace from the backend");
    }

    @Test
    @DisplayName("Should exclude default namespace when label expression is set")
    @SuppressWarnings("unchecked")
    void shouldExcludeDefaultNamespaceWhenLabelExpressionSet() throws Exception {
        Namespace ns = new Namespace();
        ns.setId("ns-1");
        ns.setPath("ns-team");
        ns.setName("team");
        ns.setLabels(Map.of("tier", "frontend"));

        when(namespacesService.list("tier=frontend")).thenReturn(new WanakuResponse<>(List.of(ns)));

        command.labelExpression = "tier=frontend";

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(0, result);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(printer).printTable(captor.capture(), eq("id"), eq("name"), eq("path"), eq("labels"));

        List<?> printed = captor.getValue();
        assertEquals(1, printed.size(), "Should contain only the matched namespace, not the default");
    }

    @Test
    @DisplayName("Should list namespaces from backend when label expression is blank")
    @SuppressWarnings("unchecked")
    void shouldListNamespacesWhenLabelExpressionBlank() throws Exception {
        Namespace ns = new Namespace();
        ns.setId("ns-1");
        ns.setPath("ns-team");
        ns.setName("team");

        when(namespacesService.list("   ")).thenReturn(new WanakuResponse<>(List.of(ns)));

        command.labelExpression = "   ";

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(0, result);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(printer).printTable(captor.capture(), eq("id"), eq("name"), eq("path"), eq("labels"));

        List<?> printed = captor.getValue();
        assertEquals(1, printed.size(), "Should contain only the namespace from the backend");
    }
}

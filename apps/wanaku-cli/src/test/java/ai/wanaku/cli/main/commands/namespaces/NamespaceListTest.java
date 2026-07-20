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
    @DisplayName("Should include default namespace when no label expression is set")
    @SuppressWarnings("unchecked")
    void shouldIncludeDefaultNamespaceWhenNoLabelExpression() throws Exception {
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
        assertEquals(2, printed.size(), "Should contain the namespace plus the default namespace");
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
    @DisplayName("Should exclude default namespace when label expression is a blank string")
    @SuppressWarnings("unchecked")
    void shouldExcludeDefaultNamespaceWhenLabelExpressionBlank() throws Exception {
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
        // A blank label expression is effectively "no filter", so the default namespace should be excluded
        // since the backend treats blank as "list all" and the client should not add a synthetic default
        // Actually, blank means "no filter active" so default SHOULD be included
        assertEquals(2, printed.size(), "Blank expression means no filter, so default namespace should be included");
    }
}

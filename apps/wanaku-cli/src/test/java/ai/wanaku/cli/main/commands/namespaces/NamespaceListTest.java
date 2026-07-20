package ai.wanaku.cli.main.commands.namespaces;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import ai.wanaku.capabilities.sdk.api.types.Namespace;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceListTest {

    @Mock
    private NamespacesService namespacesService;

    private NamespaceList command;

    @BeforeEach
    void setUp() {
        command = new NamespaceList();
        command.namespacesService = namespacesService;
        command.host = "http://localhost:8080";
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Should include default namespace when no label filter is specified")
    void shouldIncludeDefaultNamespaceWithoutFilter() throws Exception {
        Namespace ns = new Namespace();
        ns.setId("ns-1");
        ns.setPath("my-ns");
        ns.setName("my-ns");

        when(namespacesService.list(isNull())).thenReturn(new WanakuResponse<>(List.of(ns)));

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_OK, result);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(printer).printTable(captor.capture(), eq("id"), eq("name"), eq("path"), eq("labels"));
        List<?> printed = captor.getValue();
        assertEquals(2, printed.size(), "Should contain the namespace plus the default entry");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Should exclude default namespace when label expression filter is active")
    void shouldExcludeDefaultNamespaceWithLabelFilter() throws Exception {
        Namespace ns = new Namespace();
        ns.setId("ns-1");
        ns.setPath("my-ns");
        ns.setName("my-ns");
        ns.setLabels(Map.of("tier", "frontend"));

        when(namespacesService.list(eq("tier=frontend"))).thenReturn(new WanakuResponse<>(List.of(ns)));

        picocli.CommandLine cli = new picocli.CommandLine(command);
        cli.parseArgs("-e", "tier=frontend");

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_OK, result);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(printer).printTable(captor.capture(), eq("id"), eq("name"), eq("path"), eq("labels"));
        List<?> printed = captor.getValue();
        assertEquals(1, printed.size(), "Should contain only the filtered namespace, not the default");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Should return empty list when label filter matches nothing")
    void shouldReturnEmptyWhenFilterMatchesNothing() throws Exception {
        when(namespacesService.list(eq("env=staging"))).thenReturn(new WanakuResponse<>(Collections.emptyList()));

        picocli.CommandLine cli = new picocli.CommandLine(command);
        cli.parseArgs("-e", "env=staging");

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_OK, result);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(printer).printTable(captor.capture(), eq("id"), eq("name"), eq("path"), eq("labels"));
        List<?> printed = captor.getValue();
        assertTrue(printed.isEmpty(), "Should not include the default namespace when a label filter is active");
    }

    @Test
    @DisplayName("Should include default namespace when label expression is a blank string")
    @SuppressWarnings("unchecked")
    void shouldIncludeDefaultNamespaceWhenLabelExpressionBlank() throws Exception {
        Namespace ns = new Namespace();
        ns.setId("ns-1");
        ns.setPath("ns-team");
        ns.setName("team");

        when(namespacesService.list("   ")).thenReturn(new WanakuResponse<>(List.of(ns)));

        command.labelExpression = "   ";

        WanakuPrinter printer = mock(WanakuPrinter.class);
        Integer result = command.doCall(null, printer);

        assertEquals(BaseCommand.EXIT_OK, result);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(printer).printTable(captor.capture(), eq("id"), eq("name"), eq("path"), eq("labels"));

        List<?> printed = captor.getValue();
        assertEquals(2, printed.size(), "Blank expression means no filter, so default namespace should be included");
    }
}

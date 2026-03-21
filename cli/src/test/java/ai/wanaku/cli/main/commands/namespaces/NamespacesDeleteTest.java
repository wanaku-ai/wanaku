package ai.wanaku.cli.main.commands.namespaces;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.NamespacesService;

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
public class NamespacesDeleteTest {

    @Mock
    private NamespacesService namespacesService;

    private NamespacesDelete command;

    @BeforeEach
    void setUp() {
        command = new NamespacesDelete();
        command.namespacesService = namespacesService;
        command.host = "http://localhost:8080";
    }

    @Test
    @DisplayName("Should delete namespace by id")
    void shouldDeleteNamespaceById() throws Exception {
        when(namespacesService.delete("ns-1")).thenReturn(mock(Response.class));

        command.id = "ns-1";
        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(0, result);
    }

    @Test
    @DisplayName("Should return error when namespace not found")
    void shouldReturnErrorWhenNamespaceNotFound() throws Exception {
        Response notFound = Response.status(Response.Status.NOT_FOUND).build();
        when(namespacesService.delete("missing")).thenThrow(new WebApplicationException(notFound));

        command.id = "missing";
        Integer result = command.doCall(null, mock(WanakuPrinter.class));

        assertEquals(1, result);
    }
}

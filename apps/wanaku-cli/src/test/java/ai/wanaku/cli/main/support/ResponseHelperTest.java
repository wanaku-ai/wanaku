package ai.wanaku.cli.main.support;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import ai.wanaku.cli.main.commands.BaseCommand;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ResponseHelperTest {

    @Test
    void handleNotFoundReturnsErrorForNotFoundStatus() throws IOException {
        WebApplicationException ex = new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND).build());
        WanakuPrinter printer = mock(WanakuPrinter.class);

        int result = ResponseHelper.handleNotFound(ex, "Tool", "my-tool", printer);

        assertEquals(BaseCommand.EXIT_ERROR, result);
        verify(printer).printWarningMessage("Tool 'my-tool' not found: Not Found");
    }

    @Test
    void handleNotFoundReturnsErrorForOtherStatus() throws IOException {
        WebApplicationException ex = new WebApplicationException(
                Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        WanakuPrinter printer = mock(WanakuPrinter.class);

        int result = ResponseHelper.handleNotFound(ex, "Tool", "my-tool", printer);

        assertEquals(BaseCommand.EXIT_ERROR, result);
    }

    @Test
    void extractErrorMessageReadsWanakuResponseError() {
        String body = "{\"error\":{\"message\":\"Cannot update protected namespace 'ns-6'\"},\"data\":null}";
        assertEquals("Cannot update protected namespace 'ns-6'", ResponseHelper.extractErrorMessage(body));
    }

    @Test
    void extractErrorMessageReadsTextualError() {
        String body = "{\"error\":\"something went wrong\",\"data\":null}";
        assertEquals("something went wrong", ResponseHelper.extractErrorMessage(body));
    }

    @Test
    void extractErrorMessageFallsBackToRawBody() {
        assertEquals("plain text error", ResponseHelper.extractErrorMessage("plain text error"));
        assertEquals("", ResponseHelper.extractErrorMessage(""));
        assertEquals("", ResponseHelper.extractErrorMessage(null));
        assertEquals("{\"data\":null}", ResponseHelper.extractErrorMessage("{\"data\":null}"));
    }
}

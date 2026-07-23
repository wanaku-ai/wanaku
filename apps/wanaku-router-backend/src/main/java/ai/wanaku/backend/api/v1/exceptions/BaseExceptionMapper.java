package ai.wanaku.backend.api.v1.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

/**
 * A provider of a base exception mapper that converts exceptions to standardized responses.
 * <p>
 * This is the catch-all mapper for exceptions not handled by more specific mappers.
 * It includes the actual exception message in the response so that clients receive
 * actionable diagnostic information instead of a generic error.
 */
@Provider
public class BaseExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(BaseExceptionMapper.class);

    private static final String FALLBACK_MESSAGE = "Internal server error";

    @APIResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(schema = @Schema(implementation = WanakuResponse.class)))
    @Override
    public Response toResponse(Exception e) {
        LOG.error(e.getMessage(), e);

        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = FALLBACK_MESSAGE;
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new WanakuResponse<Void>(message))
                .build();
    }
}

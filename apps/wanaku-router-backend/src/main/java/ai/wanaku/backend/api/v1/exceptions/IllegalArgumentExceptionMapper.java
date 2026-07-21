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
 * Maps {@link IllegalArgumentException} to an HTTP 400 Bad Request response.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    private static final Logger LOG = Logger.getLogger(IllegalArgumentExceptionMapper.class);

    @APIResponse(
            responseCode = "400",
            description = "Bad request",
            content = @Content(schema = @Schema(implementation = WanakuResponse.class)))
    @Override
    public Response toResponse(IllegalArgumentException e) {
        LOG.warnf("Bad request: %s", e.getMessage());

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new WanakuResponse<Void>(e.getMessage()))
                .build();
    }
}

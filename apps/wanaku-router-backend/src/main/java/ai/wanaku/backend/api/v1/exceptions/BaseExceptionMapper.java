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
 */
@Provider
public class BaseExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(BaseExceptionMapper.class);

    private static final String GENERIC_ERROR = "Generic error";

    @APIResponse(
            responseCode = "500",
            description = "Generic error",
            content = @Content(schema = @Schema(implementation = WanakuResponse.class)))
    @Override
    public Response toResponse(Exception e) {
        LOG.error(e.getMessage(), e);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new WanakuResponse<Void>(GENERIC_ERROR))
                .build();
    }
}

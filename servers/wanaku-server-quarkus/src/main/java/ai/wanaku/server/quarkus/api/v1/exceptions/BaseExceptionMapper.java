package ai.wanaku.server.quarkus.api.v1.exceptions;

import ai.wanaku.api.types.WanakuResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

@Provider
public class BaseExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(BaseExceptionMapper.class);

    private static final String GENERIC_ERROR = "Generic error";

    @APIResponse(
        responseCode = "500",
        description = "Generic error",
        content = @Content(schema = @Schema(implementation = WanakuResponse.class))
    )
    @Override
    public Response toResponse(Exception e) {
        LOG.error(e);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new WanakuResponse<Void>(GENERIC_ERROR)).build();
    }
}

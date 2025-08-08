package ai.wanaku.backend.api.v1.exceptions;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.WanakuResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

@Provider
public class WanakuExceptionMapper implements ExceptionMapper<WanakuException> {
    private static final Logger LOG = Logger.getLogger(WanakuExceptionMapper.class);

    @APIResponse(
            responseCode = "500",
            description = "Wanaku error",
            content = @Content(schema = @Schema(implementation = WanakuResponse.class))
    )
    @Override
    public Response toResponse(WanakuException e) {
        LOG.error(e);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new WanakuResponse<Void>(e.getMessage())).build();
    }
}

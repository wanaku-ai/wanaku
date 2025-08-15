package ai.wanaku.backend.api.v1.exceptions;

import ai.wanaku.api.exceptions.ServiceUnavailableException;
import ai.wanaku.api.types.WanakuResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

@Provider
public class ServiceUnavailableExceptionMapper implements ExceptionMapper<ServiceUnavailableException> {
    private static final Logger LOG = Logger.getLogger(ServiceUnavailableExceptionMapper.class);

    @APIResponse(
            responseCode = "504",
            description = "Service unavailable",
            content = @Content(schema = @Schema(implementation = WanakuResponse.class)))
    @Override
    public Response toResponse(ServiceUnavailableException e) {
        LOG.error(e);

        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new WanakuResponse<Void>(e.getMessage()))
                .build();
    }
}

package ai.wanaku.backend.api.v1.exceptions;

import ai.wanaku.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.api.types.WanakuResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

/**
 * Exception mapper that converts {@link EntityAlreadyExistsException} to HTTP 409 (Conflict) responses.
 */
@Provider
public class EntityAlreadyExistsExceptionMapper implements ExceptionMapper<EntityAlreadyExistsException> {
    private static final Logger LOG = Logger.getLogger(EntityAlreadyExistsExceptionMapper.class);

    @APIResponse(
            responseCode = "409",
            description = "Entity already exists",
            content = @Content(schema = @Schema(implementation = WanakuResponse.class)))
    @Override
    public Response toResponse(EntityAlreadyExistsException e) {
        LOG.error(e);

        return Response.status(Response.Status.CONFLICT)
                .entity(new WanakuResponse<Void>(e.getMessage()))
                .build();
    }
}

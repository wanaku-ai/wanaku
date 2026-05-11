package ai.wanaku.backend.api.v1.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.exceptions.ConfigurationNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.DataStoreResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.EntityAlreadyExistsException;
import ai.wanaku.capabilities.sdk.api.exceptions.NamespaceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.PromptNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.exceptions.ToolNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;

/**
 * Fallback exception mapper for {@link WanakuException} and its subtypes.
 * <p>
 * Dedicated {@link ExceptionMapper} providers exist for most subtypes (e.g.
 * {@link ResourceNotFoundException}, {@link EntityAlreadyExistsException}).
 * JAX-RS selects the most-specific mapper first, so this mapper only fires
 * when no dedicated mapper matches.  The {@code instanceof} checks below are
 * a safety net that ensures the correct HTTP status even if a dedicated
 * mapper is accidentally removed or a new subclass is added without its own
 * mapper.
 */
@Provider
public class WanakuExceptionMapper implements ExceptionMapper<WanakuException> {
    private static final Logger LOG = Logger.getLogger(WanakuExceptionMapper.class);

    @APIResponse(
            responseCode = "500",
            description = "Wanaku error",
            content = @Content(schema = @Schema(implementation = WanakuResponse.class)))
    @Override
    public Response toResponse(WanakuException e) {
        LOG.error("Request failed", e);

        int status;
        if (e instanceof ToolNotFoundException
                || e instanceof ResourceNotFoundException
                || e instanceof PromptNotFoundException
                || e instanceof ServiceNotFoundException
                || e instanceof ConfigurationNotFoundException
                || e instanceof NamespaceNotFoundException
                || e instanceof DataStoreResourceNotFoundException) {
            status = 404;
        } else if (e instanceof EntityAlreadyExistsException) {
            status = 409;
        } else if (e instanceof ServiceUnavailableException) {
            status = 502;
        } else {
            status = 500;
        }

        return Response.status(status)
                .entity(new WanakuResponse<Void>(e.getMessage()))
                .build();
    }
}

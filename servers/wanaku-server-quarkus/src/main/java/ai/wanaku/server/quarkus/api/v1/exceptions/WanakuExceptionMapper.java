package ai.wanaku.server.quarkus.api.v1.exceptions;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.server.quarkus.api.v1.models.WanakuResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.jboss.logging.Logger;

public class WanakuExceptionMapper implements ExceptionMapper<WanakuException> {
    private static final Logger LOG = Logger.getLogger(WanakuExceptionMapper.class);

    @Override
    public Response toResponse(WanakuException e) {
        LOG.error(e);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new WanakuResponse<Void>(e.getMessage())).build();
    }
}

package ai.wanaku.server.quarkus.api.v1.exceptions;

import ai.wanaku.server.quarkus.api.v1.models.WanakuResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.jboss.logging.Logger;

public class BaseExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = Logger.getLogger(BaseExceptionMapper.class);

    private static final String GENERIC_ERROR = "Generic error";

    @Override
    public Response toResponse(Exception e) {
        LOG.error(e);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new WanakuResponse<Void>(GENERIC_ERROR)).build();
    }
}

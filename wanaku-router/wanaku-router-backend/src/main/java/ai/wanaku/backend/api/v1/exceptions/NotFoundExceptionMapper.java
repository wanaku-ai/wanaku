package ai.wanaku.backend.api.v1.exceptions;

import ai.wanaku.capabilities.sdk.api.exceptions.ConfigurationNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.DataStoreResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.NamespaceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.exceptions.ToolNotFoundException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.logging.Logger;

public abstract class NotFoundExceptionMapper<T extends Throwable> implements ExceptionMapper<T> {
    private static final Logger LOG = Logger.getLogger(NotFoundExceptionMapper.class);

    @APIResponse(responseCode = "404")
    @Override
    public Response toResponse(T exception) {
        LOG.error(exception);

        return Response.status(Response.Status.NOT_FOUND).build();
    }
}

@Provider
class ResourceNotFoundExceptionMapper extends NotFoundExceptionMapper<ResourceNotFoundException> {}

@Provider
class ToolNotFoundExceptionMapper extends NotFoundExceptionMapper<ToolNotFoundException> {}

@Provider
class ServiceNotFoundExceptionMapper extends NotFoundExceptionMapper<ServiceNotFoundException> {}

@Provider
class ConfigurationNotFoundExceptionMapper extends NotFoundExceptionMapper<ConfigurationNotFoundException> {}

@Provider
class AddrNotFoundExceptionMapper extends NotFoundExceptionMapper<NotFoundException> {}

@Provider
class NamespaceNotFoundExceptionMapper extends NotFoundExceptionMapper<NamespaceNotFoundException> {}

@Provider
class DataStoreResourceNotFoundExceptionMapper extends NotFoundExceptionMapper<DataStoreResourceNotFoundException> {}

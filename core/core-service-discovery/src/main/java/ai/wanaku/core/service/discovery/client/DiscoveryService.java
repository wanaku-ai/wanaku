package ai.wanaku.core.service.discovery.client;

import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.discovery.ServiceState;
import ai.wanaku.api.types.providers.ServiceTarget;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/v1/management/discovery")
public interface DiscoveryService {

    @Path("/register")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    RestResponse<WanakuResponse<ServiceTarget>> register(ServiceTarget serviceTarget);

    @Path("/deregister")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    Response deregister(ServiceTarget serviceTarget);

    @Path("/update/{id}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response updateState(@PathParam("id") String id, ServiceState serviceState);

    @Path("/ping")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    Response ping(String id);
}

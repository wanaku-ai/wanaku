package ai.wanaku.server.quarkus.api.v1.management.discovery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.discovery.ServiceState;
import ai.wanaku.api.types.providers.ServiceTarget;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

@ApplicationScoped
@Path("/api/v1/management/discovery")
public class DiscoveryResource {
    private static final Logger LOG = Logger.getLogger(DiscoveryResource.class);

    @Inject
    DiscoveryBean discoveryBean;

    @Path("/register")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public RestResponse<WanakuResponse<ServiceTarget>> register(ServiceTarget serviceTarget) {
        var ret = discoveryBean.registerService(serviceTarget);
        return RestResponse.ok(new WanakuResponse<>(ret));
    }

    @Path("/deregister")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deregister(ServiceTarget serviceTarget) {
        discoveryBean.deregisterService(serviceTarget);
        return Response.ok().build();
    }

    @Path("/update/{id}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateState(@PathParam("id") String id, ServiceState serviceState) {
        discoveryBean.updateState(id, serviceState);
        return Response.ok().build();
    }

    @Path("/ping")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ping(String id) {
        LOG.tracef("Service %s is pinging", id);
        discoveryBean.ping(id);
        return Response.ok().build();
    }
}

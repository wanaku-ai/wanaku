package ai.wanaku.cli.main.services;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.providers.ServiceTarget;
import java.util.List;
import java.util.Map;
import org.jboss.resteasy.reactive.RestPath;

@Path("/api/v1/management/targets")
public interface TargetsService {

    @Path("/tools/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    WanakuResponse<List<ServiceTarget>> toolsList();

    @Path("/tools/state")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Map<String, List<ActivityRecord>>> toolsState();

    @Path("/tools/configure/{service}")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    Response toolsConfigure(@RestPath("service") String service, @QueryParam("option") String option, @QueryParam("value") String value);

    @Path("/resources/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    WanakuResponse<List<ServiceTarget>> resourcesList();

    @Path("/resources/configure/{service}")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    Response resourcesConfigure(@RestPath("service") String service, @QueryParam("option") String option, @QueryParam("value") String value);

    @Path("/resources/state")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    WanakuResponse<Map<String, List<ActivityRecord>>> resourcesState();
}

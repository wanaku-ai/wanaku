package ai.wanaku.cli.main.services;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.api.types.management.State;
import java.util.List;
import java.util.Map;
import org.jboss.resteasy.reactive.RestPath;

@Path("/api/v1/management/targets")
public interface TargetsService {

    @Path("/tools/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    WanakuResponse<Map<String, Service>> toolsList();

    @Path("/tools/state")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    WanakuResponse<Map<String, List<State>>> toolsState();

    @Path("/tools/configure/{service}")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    Response toolsConfigure(@RestPath("service") String service, @QueryParam("option") String option, @QueryParam("value") String value);

    @Path("/resources/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    WanakuResponse<Map<String, Service>> resourcesList();

    @Path("/resources/configure/{service}")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    Response resourcesConfigure(@RestPath("service") String service, @QueryParam("option") String option, @QueryParam("value") String value);

    @Path("/resources/state")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    WanakuResponse<Map<String, List<State>>> resourcesState();
}

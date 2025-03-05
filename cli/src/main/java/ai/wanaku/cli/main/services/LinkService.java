package ai.wanaku.cli.main.services;

import java.util.Map;

import ai.wanaku.api.types.WanakuResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.wanaku.api.types.management.Service;
import org.jboss.resteasy.reactive.RestPath;

@Path("/api/v1/management/targets")
public interface LinkService {

    @Path("/tools/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    WanakuResponse<Map<String, Service>> toolsList();

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
}

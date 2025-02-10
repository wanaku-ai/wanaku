package org.wanaku.cli.main.services;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.wanaku.api.types.ResourceReference;

@Path("/api/v1/resources")
public interface ResourcesService {

    @POST
    @Produces({ MediaType.APPLICATION_JSON})
    @Path("/expose")
    Response expose(ResourceReference resourceReference);

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<ResourceReference> list();
}

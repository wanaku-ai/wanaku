package org.wanaku.cli.main.services;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.wanaku.api.types.ToolReference;

@Path("/api/v1/tools")
public interface ToolsService {

    @POST
    @Produces({ MediaType.APPLICATION_JSON})
    @Path("/add")
    Response add(ToolReference toolReference);
}

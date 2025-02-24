/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wanaku.server.quarkus.api.v1.tools;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.wanaku.api.types.ToolReference;

@ApplicationScoped
@Path("/api/v1/tools")
public class ToolsResource {
    private static final Logger LOG = Logger.getLogger(ToolsResource.class);

    @Inject
    ToolsBean toolsBean;

    @Path("/add")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(ToolReference resource) {
        try {
            toolsBean.add(resource);
            return Response.ok().build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to add tools %s: %s", resource.getName(), e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to expose tool").build();
        }
    }

    @Path("/list")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list() {
        try {
            List<ToolReference> list = toolsBean.list();
            return Response.ok().entity(list).build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to list tools: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to list tools").build();
        }
    }

    @Path("/remove")
    @PUT
    public Response remove(@QueryParam("tool") String tool) {
        try {
            toolsBean.remove(tool);
            return Response.ok().build();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to remove tool %s: %s", tool, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to remove tool").build();
        }
    }
}

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

package ai.wanaku.server.quarkus.api.v1.management.targets;

import java.io.IOException;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

@ApplicationScoped
@Path("/api/v1/management/targets")
public class TargetsResource {
    private static final Logger LOG = Logger.getLogger(TargetsResource.class);

    @Inject
    TargetsBean targetsBean;

    @Path("/tools/link")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    public Response toolsLink(@QueryParam("service") String service, @QueryParam("target") String target) {
        try {
            targetsBean.toolsLink(service, target);
            return Response.ok().build();
        } catch (IOException e) {
            LOG.errorf(e, "Unable to link tools to targets: %s", e.getMessage());
            return Response.serverError().build();
        }
    }

    @Path("/tools/unlink")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    public Response toolsUnlink(@QueryParam("service") String service) {
        try {
            targetsBean.toolsUnlink(service);
            return Response.ok().build();
        } catch (IOException e) {
            LOG.errorf(e, "Unable to link tools to targets: %s", e.getMessage());
            return Response.serverError().build();
        }
    }

    @Path("/tools/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    public Map<String,String> toolList() {
        return targetsBean.toolList();
    }

    @Path("/resources/link")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    public Response resourcesLink(@QueryParam("service") String service, @QueryParam("target") String target) {
        try {
            targetsBean.resourcesLink(service, target);
            return Response.ok().build();
        } catch (IOException e) {
            LOG.errorf(e, "Unable to link resources to targets: %s", e.getMessage());
            return Response.serverError().build();
        }
    }

    @Path("/resources/unlink")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    public Response resourcesUnlink(@QueryParam("service") String service) {
        try {
            targetsBean.resourcesUnlink(service);
            return Response.ok().build();
        } catch (IOException e) {
            LOG.errorf(e, "Unable to unlink resources to targets: %s", e.getMessage());
            return Response.serverError().build();
        }
    }

    @Path("/resources/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    public Map<String,String> resourcesList() {
        return targetsBean.resourcesList();
    }
}

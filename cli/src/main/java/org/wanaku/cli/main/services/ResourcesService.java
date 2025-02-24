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

package org.wanaku.cli.main.services;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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

    @Path("/remove")
    @PUT
    Response remove(@QueryParam("resource") String resource);
}

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

import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/management/targets")
public interface LinkService {

    @Path("/tools/link")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    void toolsLink(@QueryParam("service") String service, @QueryParam("target") String target);

    @Path("/tools/unlink")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    void toolsUnlink(@QueryParam("service") String service);

    @Path("/tools/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    Map<String,String> toolsList();

    @Path("/resources/link")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    void resourcesLink(@QueryParam("service") String service, @QueryParam("target") String target);

    @Path("/resources/unlink")
    @PUT
    @Consumes(MediaType.TEXT_PLAIN)
    void resourcesUnlink(@QueryParam("service") String service);

    @Path("/resources/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    Map<String,String> resourcesList();
}

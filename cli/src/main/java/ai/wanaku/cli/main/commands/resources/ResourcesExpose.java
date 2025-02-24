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

package ai.wanaku.cli.main.commands.resources;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.services.ResourcesService;
import picocli.CommandLine;

@CommandLine.Command(name = "expose",description = "Expose resources")
public class ResourcesExpose extends BaseCommand {

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Option(names = { "--location" }, description = "The of the resource", required = true, arity = "0..1")
    private String location;

    @CommandLine.Option(names = { "--type" }, description = "The type of the resource", required = true, arity = "0..1")
    private String type;

    @CommandLine.Option(names = { "--name" }, description = "A human-readable name for the resource", required = true, arity = "0..1")
    private String name;

    @CommandLine.Option(names = { "--description" }, description = "A brief description of the resource", required = true, arity = "0..1")
    private String description;

    @CommandLine.Option(names = { "--mimeType" }, description = "The MIME type of the resource (i.e.: text/plain)", required = true,
            defaultValue = "text/plain", arity = "0..1")
    private String mimeType;

    @CommandLine.Option(names = { "--param" }, description = "One or more parameters for the resource", arity = "0..n")
    private List<String> params;

    ResourcesService resourcesService;

    @Override
    public void run() {
        resourcesService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(ResourcesService.class);

        ResourceReference resource = new ResourceReference();
        resource.setLocation(location);
        resource.setType(type);
        resource.setName(name);
        resource.setDescription(description);
        resource.setMimeType(mimeType);

        if (params != null) {
            List<ResourceReference.Param> paramsList = new ArrayList<>();
            for (String paramStr : params) {
                ResourceReference.Param param = new ResourceReference.Param();

                String[] split = paramStr.split("=");
                param.setName(split[0]);

                if (split.length > 1) {
                    param.setValue(split[1]);
                }
                paramsList.add(param);
            }
            resource.setParams(paramsList);
        }

        resourcesService.expose(resource);
    }
}

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

package org.wanaku.cli.main.commands.tools;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import org.jboss.logging.Logger;
import org.wanaku.api.types.ToolReference;
import org.wanaku.cli.main.commands.BaseCommand;
import org.wanaku.cli.main.services.ToolsService;
import org.wanaku.cli.main.support.PropertyHelper;
import org.wanaku.core.util.IndexHelper;
import picocli.CommandLine;

@CommandLine.Command(name = "import",description = "Import a toolset")
public class ToolsImport extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolsImport.class);

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;


    @CommandLine.Parameters(description="Path to the toolset", arity = "1..1")
    private String path;

    ToolsService toolsService;

    @Override
    public void run() {
        try {
            toolsService = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(URI.create(host))
                    .build(ToolsService.class);

            File indexFile = new File(path);
            List<ToolReference> toolReferences = IndexHelper.loadToolsIndex(indexFile);

            for (var toolReference : toolReferences) {
                toolsService.add(toolReference);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to load tools index: %s", e.getMessage());
            throw new RuntimeException(e);
        }




    }

}

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

package org.wanaku.cli.main.commands.targets;

import java.net.URI;
import java.util.Map;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import org.wanaku.cli.main.commands.BaseCommand;
import org.wanaku.cli.main.services.LinkService;
import org.wanaku.cli.main.support.PrettyPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "list",
        description = "List targeted services")
public abstract class AbstractTargetsList extends BaseCommand {
    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    protected LinkService linkService;

    protected void initService() {
        linkService = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(host))
                .build(LinkService.class);
    }

}

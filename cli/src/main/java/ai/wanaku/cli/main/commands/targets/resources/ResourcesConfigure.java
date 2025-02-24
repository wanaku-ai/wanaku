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

package ai.wanaku.cli.main.commands.targets.resources;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import ai.wanaku.cli.main.commands.targets.AbstractTargetsConfigure;
import picocli.CommandLine;

@CommandLine.Command(name = "configure",
        description = "Configure resources providers")
public class ResourcesConfigure extends AbstractTargetsConfigure {

    @Override
    public void run() {
        initService();

        try {
            linkService.resourcesConfigure(service, option, value);
        } catch (WebApplicationException e) {
            Response response = e.getResponse();

            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                System.out.println("There is no configuration or service with that name");
            }
        }

    }
}

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

package org.wanaku.routers.camel.proxies.tools;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;
import org.wanaku.api.types.ToolReference;
import org.wanaku.routers.camel.proxies.ToolsProxy;

public class CamelEndpointProxy implements ToolsProxy {
    private static final Logger LOG = Logger.getLogger(CamelEndpointProxy.class);

    private final ProducerTemplate producer;


    public CamelEndpointProxy(CamelContext context) {
        this.producer = context.createProducerTemplate();
    }

    @Override
    public ToolResponse call(ToolReference toolReference, ToolManager.ToolArguments toolArguments) {
        try {
            producer.start();

            CamelRequest result = CamelRequest.newCamelRequest(toolReference, toolArguments);

            String s = producer.requestBody(result.uri(), result.body(), String.class);
            return ToolResponse.success(s);
        } catch (Exception e) {
            LOG.errorf("Unable to call endpoint: %s", e.getMessage(), e);
            return ToolResponse.error(e.getMessage());
        } finally {
            producer.stop();
        }
    }

    @Override
    public String name() {
        return "camel-endpoint";
    }
}

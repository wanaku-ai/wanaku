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

package ai.wanaku.routing.service;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;
import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;

@ApplicationScoped
public class HttpDelegate implements InvocationDelegate {
    private static final Logger LOG = Logger.getLogger(HttpDelegate.class);

    private final CamelContext camelContext;

    private final ProducerTemplate producer;

    public HttpDelegate(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.producer = camelContext.createProducerTemplate();
    }

    @Override
    public ToolInvokeReply invoke(ToolInvokeRequest request) {
        try {
            producer.start();

            ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request);

            LOG.infof("Invoking tool at URI: %s", parsedRequest.uri());

            String s;
            if (parsedRequest.body().isEmpty()) {
                s = producer.requestBody(parsedRequest.uri(), null, String.class);
            } else {
                s = producer.requestBody(parsedRequest.uri(), parsedRequest.body(), String.class);
            }

            return ToolInvokeReply.newBuilder().setContent(s).setIsError(false).build();
        } catch (Exception e) {
            LOG.errorf(e,"Unable to call endpoint: %s", e.getMessage(), e);
            return ToolInvokeReply.newBuilder().setContent(e.getMessage()).setIsError(true).build();
        } finally {
            producer.stop();
        }
    }

    @Override
    public Map<String, String> serviceConfigurations() {
        return Map.of();
    }

    @Override
    public Map<String, String> credentialsConfigurations() {
        return Map.of();
    }
}

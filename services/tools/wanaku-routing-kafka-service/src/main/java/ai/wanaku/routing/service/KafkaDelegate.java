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
import jakarta.inject.Inject;

import ai.wanaku.core.exchange.InvocationDelegate;
import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.config.WanakuServiceConfig;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

@ApplicationScoped
public class KafkaDelegate implements InvocationDelegate {
    private static final Logger LOG = Logger.getLogger(KafkaDelegate.class);

    @Inject
    WanakuServiceConfig config;


    private final ProducerTemplate producer;
    private final ConsumerTemplate consumer;

    public KafkaDelegate(CamelContext camelContext) {
        this.producer = camelContext.createProducerTemplate();
        this.consumer = camelContext.createConsumerTemplate();
    }

    @Override
    public ToolInvokeReply invoke(ToolInvokeRequest request) {
        Map<String, String> serviceConfigurationsMap = request.getServiceConfigurationsMap();
        String replyToTopic = serviceConfigurationsMap.get("replyToTopic");
        String bootstrapServers = serviceConfigurationsMap.get("bootstrapHost");

        try {
            producer.start();

            ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request);
            String requestUri = String.format("%s?brokers=%s", parsedRequest.uri(), bootstrapServers);
            String responseUri = String.format("kafka://%s?brokers=%s", replyToTopic, bootstrapServers);

            LOG.infof("Invoking tool at URI: %s", requestUri);
            producer.sendBody(requestUri, parsedRequest.body());

            String response = consumer.receiveBody(responseUri, String.class);

            return ToolInvokeReply.newBuilder().setContent(response).setIsError(false).build();
        } catch (Exception e) {
            LOG.errorf("Unable to call endpoint: %s", e.getMessage(), e);
            return ToolInvokeReply.newBuilder().setContent(e.getMessage()).setIsError(true).build();
        } finally {
            producer.stop();
        }
    }

    @Override
    public Map<String, String> serviceConfigurations() {
        return config.routing().service().configurations();
    }

    @Override
    public Map<String, String> credentialsConfigurations() {
        return config.routing().credentials().configurations();
    }
}

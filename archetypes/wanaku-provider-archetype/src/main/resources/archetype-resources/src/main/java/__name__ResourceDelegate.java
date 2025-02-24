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

package ai.wanaku.provider;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.jboss.logging.Logger;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;

@ApplicationScoped
public class ${name}ResourceDelegate implements ResourceAcquirerDelegate {
    private static final Logger LOG = Logger.getLogger(${name}ResourceDelegate.class);

    @Inject
    WanakuServiceConfig config;

    private final CamelContext camelContext;
    private final ConsumerTemplate consumer;

    public ${name}ResourceDelegate(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.consumer = camelContext.createConsumerTemplate();
    }

    @Override
    public ResourceReply acquire(ResourceRequest request) {
        try {
            consumer.start();
            Object o = consumer.receiveBody("write the camel URI here", 5000);

            // Adjust the return so that the content is a string (see the file provider for an example)

            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .setContent("Invalid response type from the consumer").build();
        } catch (Exception e) {
            LOG.errorf("Unable to read %s: %s", request.getType(), e.getMessage(), e);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .setContent(e.getMessage()).build();
        } finally {
            consumer.stop();
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

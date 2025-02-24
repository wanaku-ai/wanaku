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

package ai.wanaku.provider.file;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.component.file.GenericFile;
import org.jboss.logging.Logger;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;

@ApplicationScoped
public class FileResourceDelegate implements ResourceAcquirerDelegate {
    private static final Logger LOG = Logger.getLogger(FileResourceDelegate.class);

    private final CamelContext camelContext;
    private final ConsumerTemplate consumer;

    public FileResourceDelegate(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.consumer = camelContext.createConsumerTemplate();
    }

    @Override
    public ResourceReply acquire(ResourceRequest request) {
        File file = new File(request.getLocation());
        String camelUri = String.format("%s://%s?fileName=%s&noop=true&idempotent=false", request.getType(), file.getParent(), file.getName());

        try {
            consumer.start();
            Object o = consumer.receiveBody(camelUri, 5000);
            if (o instanceof GenericFile<?> genericFile) {
                String fileName = genericFile.getAbsoluteFilePath();

                return ResourceReply.newBuilder()
                        .setIsError(false)
                        .setContent(Files.readString(Path.of(fileName))).build();

            }
            LOG.errorf("Invalid response type from the consumer: %s", o != null? o.getClass().getName() : "null");
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .setContent("Invalid response type from the consumer").build();
        } catch (Exception e) {
            LOG.errorf("Unable to read file: %s", e.getMessage(), e);
            return ResourceReply.newBuilder()
                    .setIsError(true)
                    .setContent(e.getMessage()).build();
        } finally {
            consumer.stop();
        }
    }
}

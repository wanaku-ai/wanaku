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

package org.wanaku.routers.camel.proxies.resources;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.TextResourceContents;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.component.file.GenericFile;
import org.jboss.logging.Logger;
import org.wanaku.api.types.ResourceReference;
import org.wanaku.routers.camel.proxies.ResourceProxy;

/**
 * Proxies between MCP URIs and the Camel file component
 */
public class FileProxy implements ResourceProxy {
    private static final Logger LOG = Logger.getLogger(FileProxy.class);
    private final ConsumerTemplate consumer;

    public FileProxy(CamelContext camelContext) {
        this.consumer = camelContext.createConsumerTemplate();
    }

    @Override
    public String name() {
        return "file";
    }

    @Override
    public List<ResourceContents> eval(ResourceReference mcpResource) {

        File file = new File(mcpResource.getLocation());
        String camelUri = String.format("%s://%s?fileName=%s&noop=true&idempotent=false", mcpResource.getType(), file.getParent(), file.getName());

        try {
            consumer.start();
            Object o = consumer.receiveBody(camelUri, 5000);
            if (o instanceof GenericFile<?> genericFile) {
                String fileName = genericFile.getAbsoluteFilePath();

                TextResourceContents textResourceContents =
                        new TextResourceContents("file://" + mcpResource.getLocation(), Files.readString(Path.of(fileName)),
                                mcpResource.getMimeType());

                return List.of(textResourceContents);
            }

            return Collections.emptyList();
        } catch (Exception e) {
            LOG.errorf("Unable to read file: %s", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            consumer.stop();
        }
    }
}

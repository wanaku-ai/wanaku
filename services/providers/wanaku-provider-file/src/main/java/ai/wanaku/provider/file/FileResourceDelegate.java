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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import ai.wanaku.core.services.provider.AbstractResourceDelegate;
import org.apache.camel.component.file.GenericFile;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FileResourceDelegate extends AbstractResourceDelegate {
    private static final Logger LOG = Logger.getLogger(FileResourceDelegate.class);

    @Inject
    WanakuProviderConfig config;

    protected String getEndpointUri(ResourceRequest request) {
        String baseUri = config.baseUri();

        File file = new File(request.getLocation());
        return String.format(baseUri, request.getType(), file.getParent(), file.getName());
    }

    protected String coerceResponse(Object response) throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response instanceof GenericFile<?> genericFile) {
            String fileName = genericFile.getAbsoluteFilePath();

            try {
                return Files.readString(Path.of(fileName));
            } catch (IOException e) {
                throw new NonConvertableResponseException(e);
            }
        }

        throw new InvalidResponseTypeException("Invalid response type from the consumer: " + response != null? response.getClass().getName() : "null");
    }
}

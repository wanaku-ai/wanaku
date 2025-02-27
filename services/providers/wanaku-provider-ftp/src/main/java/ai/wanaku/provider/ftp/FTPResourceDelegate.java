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

package ai.wanaku.provider.ftp;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import ai.wanaku.core.services.provider.AbstractResourceDelegate;
import org.apache.camel.component.file.GenericFile;
import org.jboss.logging.Logger;

import static ai.wanaku.core.services.util.URIHelper.buildUri;

@ApplicationScoped
public class FTPResourceDelegate extends AbstractResourceDelegate {
    private static final Logger LOG = Logger.getLogger(FTPResourceDelegate.class);

    @Inject
    WanakuProviderConfig config;

    @Override
    protected String getEndpointUri(ResourceRequest request, Map<String, String> parameters) {
        return buildUri(request.getLocation(), parameters);
    }

    @Override
    protected String coerceResponse(Object response) throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response instanceof GenericFile<?> genericFile) {
            Object body = genericFile.getBody();
            if (body instanceof byte[] bytes) {
                return new String(bytes);

            }

            throw new NonConvertableResponseException("The response body is not a byte array");
        }

        throw new InvalidResponseTypeException("Invalid response type from the consumer: " + response.getClass().getName());
    }

    @Override
    public Map<String, String> serviceConfigurations() {
        Map<String, String> configurations =  config.service().configurations();

        return componentOptions(config.name(), configurations);
    }
}

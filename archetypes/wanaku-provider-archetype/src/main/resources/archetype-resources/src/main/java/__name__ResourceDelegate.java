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
import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.api.exceptions.NonConvertableResponseException;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import ai.wanaku.core.services.provider.AbstractResourceDelegate;
import org.jboss.logging.Logger;

import static ai.wanaku.core.services.util.URIHelper.buildUri;

@ApplicationScoped
public class ${name}ResourceDelegate extends AbstractResourceDelegate {
    private static final Logger LOG = Logger.getLogger(${name}ResourceDelegate.class);

    @Inject
    WanakuProviderConfig config;

    @Override
    protected String getEndpointUri(ResourceRequest request, Map<String, String> parameters) {
        /*
         * Here you build the Camel URI based on the request parameters.
         * The parameters are already merged w/ the requested ones, but
         * feel free to override if necessary.
         *
         * For instance, suppose the component has an option "fileName" and
         * you need to set it, then use:
         *
         * parameters.putIfAbsent("fileName", file.getName());
         *
         * After the map has been adjusted, just call the buildUri from URIHelper
         */
        return buildUri(config.baseUri(), request.getLocation(), parameters);
    }

    @Override
    protected String coerceResponse(Object response) throws InvalidResponseTypeException, NonConvertableResponseException {
        if (response == null) {
            throw new InvalidResponseTypeException("Invalid response type from the consumer: null");
        }

        // Here, convert the response from whatever format it is, to a String instance.
        throw new InvalidResponseTypeException("The downstream service has not implemented the response coercion method");
    }
}

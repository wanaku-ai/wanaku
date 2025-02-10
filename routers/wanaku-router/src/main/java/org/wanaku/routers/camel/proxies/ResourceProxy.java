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

package org.wanaku.routers.camel.proxies;

import java.io.File;
import java.util.List;

import org.wanaku.api.resolvers.AsyncRequestHandler;
import org.wanaku.api.types.McpRequestStatus;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ResourceProxy extends Proxy {
    /**
     * List resources that can be handled by this proxy
     * @param index the index file
     * @return
     */
    List<McpResource> list(File index);

    /**
     * Eval an MCP URI handling it as appropriate by the component
     * @param uri
     * @return
     */
    List<McpResourceData> eval(String uri);

    /**
     * Subscribe to the given MCP URI
     * @param uri
     * @return
     */
    void subscribe(String uri, AsyncRequestHandler<McpRequestStatus<McpResourceData>> callback);

}

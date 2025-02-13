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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.wanaku.api.types.McpTool;
import org.wanaku.api.types.McpToolStatus;
import org.wanaku.api.types.ToolReference;

import static org.wanaku.core.util.IndexHelper.loadToolsIndex;

/**
 * Proxies between MCP URIs and Camel components capable of handling them
 */
public interface ToolsProxy extends Proxy {

    /**
     * Whether the proxy can handle this type of tool
     * @param toolReference A tool reference instance
     * @return true if the tool can be handled or false otherwise
     */
    boolean canHandle(ToolReference toolReference);

    /**
     * List tools that can be called by this proxy
     * @param index the index file
     * @return
     */
    default List<McpTool> list(File index) {
        final List<McpTool> tools = new ArrayList<>();

        try {
            final List<ToolReference> toolReferences = loadToolsIndex(index);

            for (ToolReference toolReference : toolReferences) {
                if (canHandle(toolReference)) {
                    McpTool tool = toMcpTool(toolReference);
                    tools.add(tool);
                }
            }

            return tools;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert the tool reference stored in the index, to a McpResource
     * @param reference the tool reference to convert
     * @return the converted tool as a MCP tool instance.
     */
    McpTool toMcpTool(ToolReference reference);

    /**
     * Call a tool
     * @param tool the tool to call
     * @param properties the properties to use when calling the tool
     * @return
     */
    McpToolStatus call(McpTool tool, Map<String, Object> properties);

}

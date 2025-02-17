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

package org.wanaku.core.mcp.common.resolvers.util;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.wanaku.api.exceptions.ToolNotFoundException;
import org.wanaku.core.mcp.common.resolvers.ToolsResolver;
import org.wanaku.api.types.McpTool;
import org.wanaku.api.types.McpToolStatus;

public class NoopToolsResolver implements ToolsResolver {
    @Override
    public List<McpTool> list() {
        return List.of();
    }

    @Override
    public McpTool find(String name) throws ToolNotFoundException {
        return null;
    }

    @Override
    public McpToolStatus call(McpTool tool, Map<String, Object> properties) {
        return null;
    }

    @Override
    public File indexLocation() {
        return null;
    }
}

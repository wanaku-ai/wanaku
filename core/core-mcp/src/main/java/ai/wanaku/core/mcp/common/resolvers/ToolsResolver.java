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

package ai.wanaku.core.mcp.common.resolvers;

import java.io.File;
import java.util.Map;

import ai.wanaku.api.exceptions.ToolNotFoundException;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.mcp.common.Tool;

/**
 * A resolver that consumes MCP requests and resolves what type of tool
 * should handle it
 */
public interface ToolsResolver extends Resolver {

    /**
     * The index file containing the targets
     * @return
     */
    default File targetsIndexFile() {
        return new File(indexBaseDirectory(), DEFAULT_TARGET_TOOLS_INDEX_FILE_NAME);
    }

    /**
     * Given a reference, resolves what tool would call it
     * @param toolReference the reference to the tool
     * @return An instance of the requested tool
     * @throws ToolNotFoundException if the tools cannot be found
     */
    Tool resolve(ToolReference toolReference) throws ToolNotFoundException;

    /**
     * Retrieve configurations from the service
     * @param target the target service to retrieve configurations from
     * @return A map of configurations and their descriptions
     */
    Map<String, String> getServiceConfigurations(String target);
}

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

/**
 * A resolver that consumes MCP requests and resolves what type of tool or resource acquirer
 * should handle it
 */
public interface Resolver {
    String DEFAULT_RESOURCES_INDEX_FILE_NAME = "resources.json";
    String DEFAULT_TOOLS_INDEX_FILE_NAME = "tools.json";
    String DEFAULT_TARGET_RESOURCES_INDEX_FILE_NAME = "resources-targets.json";
    String DEFAULT_TARGET_TOOLS_INDEX_FILE_NAME = "tools-targets.json";

    /**
     * The base directory for the index file
     * @return
     */
    default File indexBaseDirectory() {
        return indexLocation().getParentFile();
    }

    /**
     * The location of the index file
     * @return
     */
    File indexLocation();
}

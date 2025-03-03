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

package ai.wanaku.routers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import ai.wanaku.core.mcp.common.resolvers.Resolver;
import ai.wanaku.core.util.IndexHelper;

/**
 * Base provider class for tools and resources
 * @param <Y> The type of the resolver
 */
abstract class AbstractProvider<Y extends Resolver> {
    private static File createSettingsDirectory(String settingsDirectory) {
        File resourcesDir = new File(settingsDirectory);
        if (!resourcesDir.exists()) {
            resourcesDir.mkdirs();
        }

        return resourcesDir;
    }

    /**
     * Initializes the persisted index of resources
     * @param resourcesPath the path to the index file
     * @param fileName the name of the index file
     * @return the File object representing the initialized index of resources
     */
    protected File initializeResourcesIndex(String resourcesPath, String fileName) {
        File settingsDirectory = createSettingsDirectory(resourcesPath);
        File indexFile = new File(settingsDirectory, fileName);
        try {
            if (!indexFile.exists()) {
                IndexHelper.saveResourcesIndex(indexFile, Collections.EMPTY_LIST);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return indexFile;
    }

    /**
     * Initializes the persisted index of resources
     * @return the File object representing the initialized index of resources
     */
    abstract File initializeIndex();

    /**
     * Gets the resolver associated with this provider
     * @return the resolver instance
     */
    abstract Y getResolver();
}

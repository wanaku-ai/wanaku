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

package org.wanaku.core.util;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.wanaku.api.types.ResourceReference;
import org.wanaku.api.types.ToolReference;

/**
 * Helper class for dealing with the resources file
 */
public class IndexHelper {


    /**
     * Load an index
     * @param indexFile
     * @return
     * @throws Exception
     */
    private static <T> List<T> loadIndex(File indexFile, Class<T> clazz) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(indexFile,
                objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    /**
     * Saves an index of resources to a file
     * @param indexFile
     * @param resourceReferences
     * @throws IOException
     */
    public static <T> void saveIndex(File indexFile, List<T> resourceReferences) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(indexFile, resourceReferences);
    }

    /**
     * Load an index of resources
     * @param indexFile
     * @return
     * @throws Exception
     */
    public static List<ResourceReference> loadResourcesIndex(File indexFile) throws Exception {
        return loadIndex(indexFile, ResourceReference.class);
    }

    /**
     * Saves an index to a file
     * @param indexFile
     * @param resourceReferences
     * @throws IOException
     */
    public static void saveResourcesIndex(File indexFile, List<ResourceReference> resourceReferences) throws IOException {
        saveIndex(indexFile, resourceReferences);
    }

    /**
     * Load an index of tools
     * @param indexFile
     * @return
     * @throws Exception
     */
    public static List<ToolReference> loadToolsIndex(File indexFile) throws Exception {
        return loadIndex(indexFile, ToolReference.class);
    }

    /**
     * Saves an index of tools to a file
     * @param indexFile
     * @param toolReferences
     * @throws IOException
     */
    public static void saveToolsIndex(File indexFile, List<ToolReference> toolReferences) throws IOException {
        saveIndex(indexFile, toolReferences);
    }
}

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

package ai.wanaku.cli.main.support;

import java.util.List;
import java.util.Map;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.ToolReference;

public class PrettyPrinter {

    public static void printParseable(final ToolReference toolReference) {
        System.out.printf("%-15s => %-15s => %-30s    %n",
                toolReference.getName(), toolReference.getType(), toolReference.getUri());
    }

    /**
     * Prints a list of references
     * @param list the list of references
     */
    public static void printTools(final List<ToolReference> list) {
        System.out.printf("%-15s    %-15s    %-30s    %n",
                "Name", "Type", "URI");

        for (ToolReference toolReference : list) {
            printParseable(toolReference);
        }
    }

    public static void printParseable(final ResourceReference resourceReference) {
        System.out.printf("%-20s => %-15s => %-30s    %s%n",
                resourceReference.getName(), resourceReference.getType(), resourceReference.getLocation(),
                resourceReference.getDescription());
    }

    /**
     * Prints a list of resources
     * @param list the list of resources
     */
    public static void printResources(final List<ResourceReference> list) {
        System.out.printf("%-20s    %-15s    %-30s    %s%n",
                "Name", "Type", "Location", "Description");

        for (ResourceReference packageInfo : list) {
            printParseable(packageInfo);
        }
    }


    public static void printParseableTarget(String service, String target) {
        System.out.printf("%-20s => %-50s%n", service, target);
    }

    /**
     * Prints a map of entries
     * @param map the map of entries
     */
    public static void printTargets(final Map<String, String> map) {
        System.out.printf("%-20s    %-50s%n",
                "Service", "Target");

        for (var entry : map.entrySet()) {
            printParseableTarget(entry.getKey(), entry.getValue());
        }
    }

}

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

package org.wanaku.cli.main.support;

import java.util.List;

import org.wanaku.api.types.ResourceReference;
import org.wanaku.api.types.ToolReference;

public class PrettyPrinter {

    public static void printParseable(final ToolReference toolReference) {
        System.out.printf("%-15s => %-15s => %-30s    %n",
                toolReference.getName(), toolReference.getType(), toolReference.getUri());
    }

    /**
     * Prints a list of packages
     * @param list the list of packages
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
     * Prints a list of packages
     * @param list the list of packages
     */
    public static void printResources(final List<ResourceReference> list) {
        System.out.printf("%-20s    %-15s    %-30s    %s%n",
                "Name", "Type", "Location", "Description");

        for (ResourceReference packageInfo : list) {
            printParseable(packageInfo);
        }
    }

}

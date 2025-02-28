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

public class PropertyHelper {
    public record PropertyDescription(String propertyName, String dataType, String description) {
    }

    public static PropertyDescription parseProperty(String propertyStr) {
        int nameDelimiter = propertyStr.indexOf(":");
        int typeDelimiter = propertyStr.indexOf(",");
        String propertyName = propertyStr.substring(0, nameDelimiter);
        String dataType = propertyStr.substring(nameDelimiter + 1, typeDelimiter);
        String description = propertyStr.substring(typeDelimiter + 1);
        return new PropertyDescription(propertyName, dataType, description);
    }
}

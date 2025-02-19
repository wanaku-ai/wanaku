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

package org.wanaku.core.exchange;


public record ParsedToolInvokeRequest(String uri, String body) {

    public static ParsedToolInvokeRequest parseRequest(ToolInvokeRequest toolInvokeRequest) {
        String uri = toolInvokeRequest.getUri();
        String body = null;
        for (var t : toolInvokeRequest.getArgumentsMap().entrySet()) {
            if (!t.getKey().equals("_body")) {
                Object o = toolInvokeRequest.getArgumentsMap().get(t.getKey());
                uri = uri.replace(String.format("{%s}", t.getKey()), o.toString());
            } else {
                body = toolInvokeRequest.getArgumentsMap().get("_body").toString();
            }
        }

        if (body == null) {
            body = "";
        }

        return new ParsedToolInvokeRequest(uri, body);
    }
}

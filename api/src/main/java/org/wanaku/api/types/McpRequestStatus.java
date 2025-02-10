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

package org.wanaku.api.types;

import java.util.Optional;

/**
 * Holds the status for a MCP request
 * @param <T>
 */
public class McpRequestStatus<T> {

    public enum Status {
        SUCCESS(0, "The request was successful"),
        PARSE_ERROR(-32700, "Invalid JSON was received by the server. An error occurred on the server while parsing the JSON text."),
        INVALID_REQUEST(-32600, "The JSON sent is not a valid Request object"),
        METHOD_NOT_FOUND(-32601, "The method does not exist / is not available"),
        INVALID_PARAMS(-32602, "Invalid method parameter(s)"),
        INTERNAL_ERROR(-32603, "Internal JSON-RPC error"),
        SERVER_ERROR(-32000, "Server error"),
        SUBSCRIPTION_UNSUPPORTED(-32001, "Subscription is not supported for this resource");

        private final int code;
        private final String description;

        Status(int code, String description) {
            this.code = code;
            this.description = description;
        }

        /**
         * The error code (if any, otherwise 0)
         */
        public int getCode() {
            return code;
        }

        /**
         * The error message (if any, otherwise null)
         */
        public String getDescription() {
            return description;
        }
    }

    /**
     * The status for the request
     */
    public Status status;

    /**
     * The data for the request. It may contain null
     */
    public Optional<T> data;
}

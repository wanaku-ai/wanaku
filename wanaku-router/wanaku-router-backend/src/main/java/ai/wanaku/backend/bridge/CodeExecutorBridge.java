/*
 * Copyright 2026 Wanaku AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.wanaku.backend.bridge;

import java.util.Iterator;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionRequest;
import ai.wanaku.core.exchange.v1.CodeExecutionReply;

/**
 * Bridge interface for code execution services.
 * <p>
 * This interface defines the contract for code execution bridges, which are
 * responsible for routing code execution requests to appropriate backend
 * services based on engine type and programming language.
 * </p>
 */
public interface CodeExecutorBridge extends Bridge {

    /**
     * Executes code on a remote code execution service.
     * <p>
     * This method locates an appropriate code execution service based on the
     * engine type and language, then streams the execution results back to
     * the caller.
     * </p>
     *
     * @param engineType the execution engine type (e.g., "jvm", "interpreted")
     * @param language the programming language (e.g., "java", "python")
     * @param request the code execution request containing the code and parameters
     * @return an iterator over the streaming code execution replies
     */
    Iterator<CodeExecutionReply> executeCode(String engineType, String language, CodeExecutionRequest request);
}

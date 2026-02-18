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
package ai.wanaku.backend.api.v2.codeexecution;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Iterator;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;
import io.smallrye.reactive.messaging.MutinyEmitter;
import ai.wanaku.backend.bridge.CodeExecutorBridge;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionEvent;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionEventType;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionRequest;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionResponse;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionTask;
import ai.wanaku.core.exchange.v1.CodeExecutionReply;
import ai.wanaku.core.exchange.v1.ExecutionStatus;
import ai.wanaku.core.exchange.v1.OutputType;
import ai.wanaku.core.persistence.infinispan.codeexecution.InfinispanCodeTaskRepository;

/**
 * Business logic for code execution operations.
 * <p>
 * This bean handles the core business logic for code execution tasks,
 * including task creation, storage, and result streaming via Server-Sent Events.
 * </p>
 * <p>
 * Execution requests are routed to appropriate code execution services discovered
 * via the capabilities repository, with communication handled through the gRPC bridge.
 * </p>
 */
@ApplicationScoped
public class CodeExecutionBean {
    private static final Logger LOG = Logger.getLogger(CodeExecutionBean.class);

    @Inject
    Instance<InfinispanCodeTaskRepository> infinispanCodeTaskRepositoryInstance;

    @Inject
    Instance<CodeExecutorBridge> codeExecutorBridgeInstance;

    @Inject
    ManagedExecutor managedExecutor;

    @Inject
    @Channel("code-execution-event")
    @OnOverflow(OnOverflow.Strategy.DROP)
    MutinyEmitter<CodeExecutionEvent> codeEventEmitter;

    private InfinispanCodeTaskRepository taskRepository;

    private CodeExecutorBridge codeExecutionBridge;

    @PostConstruct
    public void init() {
        taskRepository = infinispanCodeTaskRepositoryInstance.get();
        codeExecutionBridge = codeExecutorBridgeInstance.get();
    }

    /**
     * Submits code for execution and creates a new task.
     * <p>
     * This method generates a unique task ID, creates a task object,
     * stores it in memory, and returns a response containing the task ID
     * and SSE stream URL.
     * </p>
     *
     * @param engineType the execution engine type (e.g., "jvm", "interpreted")
     * @param language the programming language (e.g., "java", "python")
     * @param request the code execution request
     * @param baseUrl the base URL for constructing the SSE stream URL
     * @return a response containing task details and SSE URL
     */
    public CodeExecutionResponse submitExecution(
            String engineType, String language, CodeExecutionRequest request, String baseUrl) {

        LOG.infof("Creating code execution task (engine=%s, language=%s)", engineType, language);

        // Create task object
        CodeExecutionTask task = new CodeExecutionTask(null, request, language, engineType);

        // Store task (repository will generate the ID)
        CodeExecutionTask storedTask = taskRepository.store(task);
        String taskId = storedTask.getTaskId();

        // Build SSE stream URL
        String sseUrl =
                String.format("%s/api/v2/code-execution-engine/%s/%s/%s", baseUrl, engineType, language, taskId);

        // Dispatch the task for execution
        final Iterator<CodeExecutionReply> codeExecutionReplyIterator =
                codeExecutionBridge.executeCode(engineType, language, request);

        managedExecutor.runAsync(() -> consumeEvents(codeExecutionReplyIterator, taskId));

        LOG.debugf("Task %s created with SSE URL: %s", taskId, sseUrl);

        // Return response using SDK record factory method
        return CodeExecutionResponse.createPending(taskId, sseUrl);
    }

    public void consumeEvents(Iterator<CodeExecutionReply> events, String taskId) {
        try {
            while (events.hasNext()) {
                streamExecution(events.next(), taskId);
            }
        } finally {
            LOG.infof("Task %s finished", taskId);
            taskRepository.remove(taskId);
        }
    }

    private void emitEvent(CodeExecutionEvent event) {
        boolean hasRequests = codeEventEmitter.hasRequests();
        if (hasRequests) {
            LOG.infof("Emitting event for task %s: %s", event.getTaskId(), event.getOutput());
            codeEventEmitter.sendAndForget(event);
        } else {
            LOG.trace("No pending consumers to send the request");
        }
    }

    /**
     * Streams execution results via Server-Sent Events.
     * <p>
     * This method retrieves the task, executes the code via the gRPC bridge,
     * and streams the execution output through the SSE connection.
     * </p>
     *
     * @param reply the reply received
     * @param taskId the task UUID
     */
    private void streamExecution(CodeExecutionReply reply, String taskId) {

        final long timestamp = reply.hasTimestamp()
                ? reply.getTimestamp().getSeconds() * 1000
                        + reply.getTimestamp().getNanos() / 1_000_000
                : System.currentTimeMillis();

        CodeExecutionEvent codeExecutionEvent = new CodeExecutionEvent();
        codeExecutionEvent.setTaskId(taskId);

        final ExecutionStatus status = reply.getStatus();
        switch (status) {
            case EXECUTION_STATUS_PENDING: {
                codeExecutionEvent.setEventType(CodeExecutionEventType.STARTED);
                break;
            }
            case EXECUTION_STATUS_RUNNING: {
                codeExecutionEvent.setEventType(CodeExecutionEventType.OUTPUT);
                break;
            }
            case EXECUTION_STATUS_COMPLETED: {
                codeExecutionEvent.setEventType(CodeExecutionEventType.COMPLETED);
                break;
            }
            case EXECUTION_STATUS_FAILED: {
                codeExecutionEvent.setEventType(CodeExecutionEventType.FAILED);
                break;
            }

            case EXECUTION_STATUS_TIMEOUT: {
                codeExecutionEvent.setEventType(CodeExecutionEventType.TIMEOUT);
                break;
            }
            case EXECUTION_STATUS_CANCELLED: {
                codeExecutionEvent.setEventType(CodeExecutionEventType.CANCELLED);
                break;
            }
            case UNRECOGNIZED:
                codeExecutionEvent.setEventType(CodeExecutionEventType.ERROR);
                break;
        }

        codeExecutionEvent.setTimestamp(timestamp);

        // Send output content
        StringBuffer sb = new StringBuffer();
        OutputType outputType = reply.getOutputType();
        for (String content : reply.getContentList()) {
            if (outputType == OutputType.OUTPUT_TYPE_STDERR) {
                sb.append("STDERR: ");
                sb.append(content);
                sb.append("\n");
            } else {
                sb.append(content);
                sb.append("\n");
            }
        }

        codeExecutionEvent.setOutput(sb.toString());

        // Check for completion
        if (outputType == OutputType.OUTPUT_TYPE_COMPLETION || status == ExecutionStatus.EXECUTION_STATUS_COMPLETED) {
            codeExecutionEvent.setExitCode(reply.getExitCode());
        }

        if (status == ExecutionStatus.EXECUTION_STATUS_FAILED) {
            String errorMsg = reply.getContentCount() > 0 ? reply.getContent(0) : "Execution failed";
            codeExecutionEvent.setMessage(errorMsg);
        }

        emitEvent(codeExecutionEvent);
    }
}

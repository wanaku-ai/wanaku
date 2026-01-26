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
package ai.wanaku.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Event representing a tool call invocation lifecycle.
 * <p>
 * This event captures the complete lifecycle of a tool call, from start to completion or failure,
 * including timing, arguments, responses, and error categorization for debugging purposes.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallEvent {
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern SENSITIVE_HEADER_PATTERN =
            Pattern.compile("(?i)(authorization|x-api-key|api-key|auth-token|bearer|cookie|session)");
    private static final Pattern SENSITIVE_FIELD_PATTERN =
            Pattern.compile("(?i)(password|secret|token|apikey|api_key|credentials)");

    /**
     * Event type indicating the lifecycle stage.
     */
    public enum EventType {
        STARTED("started"),
        COMPLETED("completed"),
        FAILED("failed");

        private final String value;

        EventType(String value) {
            this.value = value;
        }

        public String asValue() {
            return value;
        }
    }

    /**
     * Error category for classifying tool call failures.
     */
    public enum ErrorCategory {
        NONE("none"),
        SERVICE_UNAVAILABLE("service_unavailable"),
        TOOL_DEFINITION_ERROR("tool_definition_error"),
        INVALID_ARGUMENTS("invalid_arguments"),
        EXECUTION_ERROR("execution_error"),
        UNKNOWN("unknown");

        private final String value;

        ErrorCategory(String value) {
            this.value = value;
        }

        public String asValue() {
            return value;
        }
    }

    // Event metadata
    private String eventId;
    private EventType eventType;
    private Instant timestamp;

    // Tool information
    private String toolName;
    private String toolType;
    private String connectionId;

    // Service information
    private String serviceId;
    private String serviceAddress;

    // Request information
    private Map<String, String> arguments;
    private Map<String, String> headers;
    private String body;
    private String configurationURI;
    private String secretsURI;

    // Response information
    private Boolean isError;
    private String content;
    private Long duration;

    // Error information
    private ErrorCategory errorCategory;
    private String errorMessage;
    private String errorDetails;

    /**
     * Private constructor for factory methods.
     */
    private ToolCallEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.errorCategory = ErrorCategory.NONE;
    }

    /**
     * Creates a STARTED event for a tool call.
     *
     * @param toolName the name of the tool being invoked
     * @param toolType the type of the tool
     * @param connectionId the connection ID
     * @param serviceId the service ID
     * @param serviceAddress the service address
     * @param arguments the tool arguments
     * @param headers the request headers
     * @param body the request body
     * @param configurationURI the configuration URI
     * @param secretsURI the secrets URI
     * @return a STARTED event
     */
    public static ToolCallEvent started(
            String toolName,
            String toolType,
            String connectionId,
            String serviceId,
            String serviceAddress,
            Map<String, String> arguments,
            Map<String, String> headers,
            String body,
            String configurationURI,
            String secretsURI) {
        ToolCallEvent event = new ToolCallEvent();
        event.eventType = EventType.STARTED;
        event.toolName = toolName;
        event.toolType = toolType;
        event.connectionId = connectionId;
        event.serviceId = serviceId;
        event.serviceAddress = serviceAddress;
        event.arguments = arguments;
        event.headers = redactHeaders(headers);
        event.body = redactBody(body);
        event.configurationURI = configurationURI;
        event.secretsURI = redactSecretsURI(secretsURI);
        return event;
    }

    /**
     * Creates a COMPLETED event for a successful tool call.
     *
     * @param eventId the original event ID from the STARTED event
     * @param content the response content
     * @param duration the execution duration in milliseconds
     * @return a COMPLETED event
     */
    public static ToolCallEvent completed(String eventId, String content, long duration) {
        ToolCallEvent event = new ToolCallEvent();
        event.eventId = eventId;
        event.eventType = EventType.COMPLETED;
        event.isError = false;
        event.content = content;
        event.duration = duration;
        return event;
    }

    /**
     * Creates a FAILED event for a failed tool call.
     *
     * @param eventId the original event ID from the STARTED event
     * @param errorCategory the error category
     * @param errorMessage the error message
     * @param errorDetails detailed error information
     * @param duration the execution duration in milliseconds
     * @return a FAILED event
     */
    public static ToolCallEvent failed(
            String eventId, ErrorCategory errorCategory, String errorMessage, String errorDetails, long duration) {
        ToolCallEvent event = new ToolCallEvent();
        event.eventId = eventId;
        event.eventType = EventType.FAILED;
        event.isError = true;
        event.errorCategory = errorCategory;
        event.errorMessage = errorMessage;
        event.errorDetails = errorDetails;
        event.duration = duration;
        return event;
    }

    /**
     * Redacts sensitive headers.
     */
    private static Map<String, String> redactHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        Map<String, String> redacted = new HashMap<>();
        headers.forEach((key, value) -> {
            if (SENSITIVE_HEADER_PATTERN.matcher(key).find()) {
                redacted.put(key, REDACTED);
            } else {
                redacted.put(key, value);
            }
        });
        return redacted;
    }

    /**
     * Redacts sensitive fields in the body.
     */
    private static String redactBody(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        // Simple redaction for JSON-like structures
        // This is a basic implementation - could be enhanced with proper JSON parsing
        String redacted = body;
        if (SENSITIVE_FIELD_PATTERN.matcher(body).find()) {
            // Mark that sensitive data may be present
            redacted = "[Body may contain sensitive data - partially redacted]";
        }
        return redacted;
    }

    /**
     * Redacts secrets URI.
     */
    private static String redactSecretsURI(String secretsURI) {
        if (secretsURI == null || secretsURI.isEmpty()) {
            return secretsURI;
        }
        return REDACTED;
    }

    // Getters and setters

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolType() {
        return toolType;
    }

    public void setToolType(String toolType) {
        this.toolType = toolType;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceAddress() {
        return serviceAddress;
    }

    public void setServiceAddress(String serviceAddress) {
        this.serviceAddress = serviceAddress;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, String> arguments) {
        this.arguments = arguments;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getConfigurationURI() {
        return configurationURI;
    }

    public void setConfigurationURI(String configurationURI) {
        this.configurationURI = configurationURI;
    }

    public String getSecretsURI() {
        return secretsURI;
    }

    public void setSecretsURI(String secretsURI) {
        this.secretsURI = secretsURI;
    }

    public Boolean getIsError() {
        return isError;
    }

    public void setIsError(Boolean isError) {
        this.isError = isError;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public ErrorCategory getErrorCategory() {
        return errorCategory;
    }

    public void setErrorCategory(ErrorCategory errorCategory) {
        this.errorCategory = errorCategory;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }
}

package ai.wanaku.backend.common;

import java.util.UUID;
import ai.wanaku.capabilities.sdk.api.types.WanakuEntity;

/**
 * Persisted audit record for tool call events.
 * Contains only non-sensitive fields safe for long-term storage.
 */
public class ToolCallRecord implements WanakuEntity<String> {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private String id;
    private String eventId;
    private String eventType;
    private long timestamp;
    private String toolName;
    private String toolType;
    private String connectionId;
    private String serviceId;
    private String serviceAddress;
    private boolean error;
    private long duration;
    private String errorCategory;
    private String errorMessage;
    private String argumentKeys;

    public ToolCallRecord() {}

    public static ToolCallRecord fromEvent(ToolCallEvent event) {
        ToolCallRecord record = new ToolCallRecord();
        record.id = UUID.randomUUID().toString();
        record.eventId = event.getEventId();
        record.eventType = event.getEventType() != null ? event.getEventType().asValue() : null;
        record.timestamp = event.getTimestamp() != null ? event.getTimestamp().toEpochMilli() : 0;
        record.toolName = event.getToolName();
        record.toolType = event.getToolType();
        record.connectionId = event.getConnectionId();
        record.serviceId = event.getServiceId();
        record.serviceAddress = event.getServiceAddress();
        record.error = Boolean.TRUE.equals(event.getIsError());
        record.duration = event.getDuration() != null ? event.getDuration() : 0;
        record.errorCategory =
                event.getErrorCategory() != null ? event.getErrorCategory().asValue() : null;
        record.errorMessage = truncate(event.getErrorMessage());

        if (event.getArguments() != null && !event.getArguments().isEmpty()) {
            record.argumentKeys = String.join(",", event.getArguments().keySet());
        }

        return record;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
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

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public void setErrorCategory(String errorCategory) {
        this.errorCategory = errorCategory;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getArgumentKeys() {
        return argumentKeys;
    }

    public void setArgumentKeys(String argumentKeys) {
        this.argumentKeys = argumentKeys;
    }
}

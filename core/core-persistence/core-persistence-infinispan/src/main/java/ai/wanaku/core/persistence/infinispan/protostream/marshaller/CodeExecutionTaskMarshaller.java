package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionRequest;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionStatus;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionTask;
import java.io.IOException;
import java.time.Instant;
import org.infinispan.protostream.MessageMarshaller;

public class CodeExecutionTaskMarshaller implements MessageMarshaller<CodeExecutionTask> {

    @Override
    public String getTypeName() {
        return CodeExecutionTask.class.getCanonicalName();
    }

    @Override
    public Class<? extends CodeExecutionTask> getJavaClass() {
        return CodeExecutionTask.class;
    }

    @Override
    public CodeExecutionTask readFrom(ProtoStreamReader reader) throws IOException {
        String taskId = reader.readString("taskId");
        CodeExecutionRequest request = reader.readObject("request", CodeExecutionRequest.class);
        String engineType = reader.readString("engineType");
        String language = reader.readString("language");
        CodeExecutionStatus status = reader.readEnum("status", CodeExecutionStatus.class);
        Long submittedAtMillis = reader.readLong("submittedAt");
        Long startedAtMillis = reader.readLong("startedAt");
        Long completedAtMillis = reader.readLong("completedAt");
        Integer exitCode = reader.readInt("exitCode");

        CodeExecutionTask task = new CodeExecutionTask();
        task.setTaskId(taskId);
        task.setRequest(request);
        task.setEngineType(engineType);
        task.setLanguage(language);
        task.setStatus(status != null ? status : CodeExecutionStatus.PENDING);

        if (submittedAtMillis != null && submittedAtMillis != 0) {
            task.setSubmittedAt(Instant.ofEpochMilli(submittedAtMillis));
        }
        if (startedAtMillis != null && startedAtMillis != 0) {
            task.setStartedAt(Instant.ofEpochMilli(startedAtMillis));
        }
        if (completedAtMillis != null && completedAtMillis != 0) {
            task.setCompletedAt(Instant.ofEpochMilli(completedAtMillis));
        }
        if (exitCode != null) {
            task.setExitCode(exitCode);
        }

        return task;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, CodeExecutionTask task) throws IOException {
        writer.writeString("taskId", task.getTaskId());
        writer.writeObject("request", task.getRequest(), CodeExecutionRequest.class);
        writer.writeString("engineType", task.getEngineType());
        writer.writeString("language", task.getLanguage());
        writer.writeEnum("status", task.getStatus());

        if (task.getSubmittedAt() != null) {
            writer.writeLong("submittedAt", task.getSubmittedAt().toEpochMilli());
        }
        if (task.getStartedAt() != null) {
            writer.writeLong("startedAt", task.getStartedAt().toEpochMilli());
        }
        if (task.getCompletedAt() != null) {
            writer.writeLong("completedAt", task.getCompletedAt().toEpochMilli());
        }
        if (task.getExitCode() != null) {
            writer.writeInt("exitCode", task.getExitCode());
        }
    }
}

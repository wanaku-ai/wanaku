package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import org.infinispan.protostream.EnumMarshaller;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionStatus;

public class CodeExecutionStatusMarshaller implements EnumMarshaller<CodeExecutionStatus> {

    @Override
    public Class<CodeExecutionStatus> getJavaClass() {
        return CodeExecutionStatus.class;
    }

    @Override
    public String getTypeName() {
        return CodeExecutionStatus.class.getCanonicalName();
    }

    @Override
    public CodeExecutionStatus decode(int enumValue) {
        return switch (enumValue) {
            case 0 -> CodeExecutionStatus.PENDING;
            case 1 -> CodeExecutionStatus.RUNNING;
            case 2 -> CodeExecutionStatus.COMPLETED;
            case 3 -> CodeExecutionStatus.FAILED;
            case 4 -> CodeExecutionStatus.CANCELLED;
            case 5 -> CodeExecutionStatus.TIMEOUT;
            default -> throw new IllegalArgumentException("Unknown CodeExecutionStatus value: " + enumValue);
        };
    }

    @Override
    public int encode(CodeExecutionStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case RUNNING -> 1;
            case COMPLETED -> 2;
            case FAILED -> 3;
            case CANCELLED -> 4;
            case TIMEOUT -> 5;
        };
    }
}

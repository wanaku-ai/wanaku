package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.infinispan.protostream.MessageMarshaller;

public class CodeExecutionRequestMarshaller implements MessageMarshaller<CodeExecutionRequest> {

    @Override
    public String getTypeName() {
        return CodeExecutionRequest.class.getCanonicalName();
    }

    @Override
    public Class<? extends CodeExecutionRequest> getJavaClass() {
        return CodeExecutionRequest.class;
    }

    @Override
    public CodeExecutionRequest readFrom(ProtoStreamReader reader) throws IOException {
        String code = reader.readString("code");
        Long timeout = reader.readLong("timeout");
        Map<String, String> environment = reader.readMap("environment", new HashMap<>(), String.class, String.class);
        List<String> arguments = reader.readCollection("arguments", new ArrayList<>(), String.class);

        CodeExecutionRequest request = new CodeExecutionRequest();
        if (code != null && !code.isEmpty()) {
            request.setCode(code);
        }
        if (timeout != null) {
            request.setTimeout(timeout);
        }
        if (environment != null) {
            request.setEnvironment(environment);
        }
        if (arguments != null) {
            request.setArguments(arguments);
        }

        return request;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, CodeExecutionRequest request) throws IOException {
        writer.writeString("code", request.getCode());
        writer.writeLong("timeout", request.getTimeout());
        writer.writeMap("environment", request.getEnvironment(), String.class, String.class);
        writer.writeCollection("arguments", request.getArguments(), String.class);
    }
}

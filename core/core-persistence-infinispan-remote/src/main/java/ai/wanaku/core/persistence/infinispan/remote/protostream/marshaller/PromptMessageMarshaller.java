package ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller;

import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.capabilities.sdk.api.types.PromptContent;
import ai.wanaku.capabilities.sdk.api.types.PromptMessage;

public class PromptMessageMarshaller implements MessageMarshaller<PromptMessage> {

    @Override
    public String getTypeName() {
        return "ai.wanaku.capabilities.sdk.api.types.PromptReference.PromptMessage";
    }

    @Override
    public Class<? extends PromptMessage> getJavaClass() {
        return PromptMessage.class;
    }

    @Override
    public PromptMessage readFrom(ProtoStreamReader reader) throws IOException {
        PromptMessage message = new PromptMessage();
        message.setRole(reader.readString("role"));
        message.setContent(reader.readObject("content", PromptContent.class));
        return message;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, PromptMessage message) throws IOException {
        writer.writeString("role", message.getRole());
        if (message.getContent() != null) {
            writer.writeObject("content", message.getContent(), PromptContent.class);
        }
    }
}

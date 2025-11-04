package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.PromptContent;
import ai.wanaku.api.types.PromptMessage;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;
import org.infinispan.protostream.WrappedMessage;

/**
 * Protostream marshaller for PromptMessage.
 * Handles the nested PromptContent with oneof by using WrappedMessage.
 */
public class PromptMessageMarshaller implements MessageMarshaller<PromptMessage> {

    @Override
    public String getTypeName() {
        return "ai.wanaku.api.types.PromptReference.PromptMessage";
    }

    @Override
    public Class<? extends PromptMessage> getJavaClass() {
        return PromptMessage.class;
    }

    @Override
    public PromptMessage readFrom(ProtoStreamReader reader) throws IOException {
        PromptMessage message = new PromptMessage();
        message.setRole(reader.readString("role"));

        // Read content as WrappedMessage and extract the actual value
        WrappedMessage wrappedContent = reader.readObject("content", WrappedMessage.class);
        if (wrappedContent != null) {
            Object content = wrappedContent.getValue();
            if (content instanceof PromptContent promptContent) {
                message.setContent(promptContent);
            }
        }

        return message;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, PromptMessage message) throws IOException {
        writer.writeString("role", message.getRole());

        // Wrap content and write it
        if (message.getContent() != null) {
            writer.writeObject("content", new WrappedMessage(message.getContent()), WrappedMessage.class);
        }
    }
}

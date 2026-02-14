package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import java.util.ArrayList;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.capabilities.sdk.api.types.PromptMessage;
import ai.wanaku.capabilities.sdk.api.types.PromptReference;
import ai.wanaku.capabilities.sdk.api.types.PromptReference.PromptArgument;

/**
 * Protostream marshaller for PromptReference.
 */
public class PromptReferenceMarshaller implements MessageMarshaller<PromptReference> {

    @Override
    public String getTypeName() {
        return PromptReference.class.getCanonicalName();
    }

    @Override
    public Class<? extends PromptReference> getJavaClass() {
        return PromptReference.class;
    }

    @Override
    public PromptReference readFrom(ProtoStreamReader reader) throws IOException {
        PromptReference ref = new PromptReference();
        ref.setId(reader.readString("id"));
        ref.setName(reader.readString("name"));
        ref.setDescription(reader.readString("description"));
        ref.setMessages(reader.readCollection("messages", new ArrayList<>(), PromptMessage.class));
        ref.setArguments(reader.readCollection("arguments", new ArrayList<>(), PromptArgument.class));
        ref.setToolReferences(reader.readCollection("tool_references", new ArrayList<>(), String.class));
        ref.setNamespace(reader.readString("namespace"));
        ref.setConfigurationURI(reader.readString("configuration_uri"));
        return ref;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, PromptReference ref) throws IOException {
        writer.writeString("id", ref.getId());
        writer.writeString("name", ref.getName());
        writer.writeString("description", ref.getDescription());
        writer.writeCollection("messages", ref.getMessages(), PromptMessage.class);
        writer.writeCollection("arguments", ref.getArguments(), PromptArgument.class);
        writer.writeCollection("tool_references", ref.getToolReferences(), String.class);
        writer.writeString("namespace", ref.getNamespace());
        writer.writeString("configuration_uri", ref.getConfigurationURI());
    }
}
